package com.bookstore.catalog;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.bookstore.catalog.CatalogDtos.HomeView;
import com.bookstore.catalog.CatalogDtos.ProductDetail;
import com.bookstore.catalog.CatalogDtos.ProductPage;
import com.bookstore.catalog.CatalogDtos.ProductRequest;
import com.bookstore.catalog.CatalogDtos.ProductView;
import com.bookstore.catalog.CatalogDtos.ReviewRequest;
import com.bookstore.catalog.CatalogDtos.ReviewView;
import com.bookstore.catalog.CatalogDtos.WriterView;
import com.bookstore.common.ApiException;
import com.bookstore.member.User;
import com.bookstore.member.UserRepository;
import com.bookstore.order.OrderRepository;
import com.bookstore.order.OrderRepository.PurchasedProduct;
import com.bookstore.security.AuthUser;
import com.bookstore.security.CurrentUser;

import jakarta.persistence.criteria.Predicate;

/** Catalog service: browse, search, product page, recommendations, reviews, wishlist. */
@Service
@Transactional(readOnly = true)
public class CatalogService {

    private static final int HOME_SIZE = 8;

    private final ProductRepository products;
    private final CategoryRepository categories;
    private final BrandRepository brands;
    private final WriterRepository writers;
    private final ReviewRepository reviews;
    private final WishlistRepository wishlist;
    private final OrderRepository orders;
    private final UserRepository users;
    private final ProductMapper mapper;

    public CatalogService(ProductRepository products, CategoryRepository categories, BrandRepository brands,
            WriterRepository writers, ReviewRepository reviews, WishlistRepository wishlist, OrderRepository orders,
            UserRepository users, ProductMapper mapper) {
        this.products = products;
        this.categories = categories;
        this.brands = brands;
        this.writers = writers;
        this.reviews = reviews;
        this.wishlist = wishlist;
        this.orders = orders;
        this.users = users;
        this.mapper = mapper;
    }

    // ---- browse ----

    public ProductPage search(String q, Long categoryId, Long brandId, String language, BookFormat format,
            BigDecimal minPrice, BigDecimal maxPrice, String sort, int page, int size) {
        boolean admin = CurrentUser.isAdmin();
        Specification<Product> spec = (root, query, cb) -> {
            List<Predicate> ps = new ArrayList<>();
            if (!admin) {
                ps.add(cb.isTrue(root.get("active")));
            }
            if (q != null && !q.isBlank()) {
                String like = "%" + q.trim().toLowerCase() + "%";
                ps.add(cb.or(cb.like(cb.lower(root.get("title")), like),
                        cb.like(cb.lower(root.get("description")), like),
                        cb.like(cb.lower(root.get("writer").get("name")), like)));
            }
            if (categoryId != null) {
                ps.add(cb.equal(root.get("category").get("id"), categoryId));
            }
            if (brandId != null) {
                ps.add(cb.equal(root.get("brand").get("id"), brandId));
            }
            if (language != null && !language.isBlank()) {
                ps.add(cb.equal(cb.lower(root.get("language")), language.trim().toLowerCase()));
            }
            if (format != null) {
                ps.add(cb.equal(root.get("bookFormat"), format));
            }
            if (minPrice != null) {
                ps.add(cb.greaterThanOrEqualTo(root.get("price"), minPrice));
            }
            if (maxPrice != null) {
                ps.add(cb.lessThanOrEqualTo(root.get("price"), maxPrice));
            }
            return cb.and(ps.toArray(new Predicate[0]));
        };
        Pageable pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100), sortFor(sort));
        Page<Product> result = products.findAll(spec, pageable);
        return new ProductPage(mapper.views(result.getContent()), result.getNumber(), result.getSize(),
                result.getTotalElements(), result.getTotalPages());
    }

    private static Sort sortFor(String sort) {
        String s = sort == null ? "relevance" : sort;
        return switch (s) {
            case "price_asc" -> Sort.by("price").ascending().and(Sort.by("id"));
            case "price_desc" -> Sort.by("price").descending().and(Sort.by("id"));
            case "newest" -> Sort.by("createdAt").descending().and(Sort.by("id").descending());
            case "rating" -> Sort.by("ratingAvg").descending().and(Sort.by("id"));
            default -> Sort.by("copiesSold").descending().and(Sort.by("ratingAvg").descending()).and(Sort.by("id"));
        };
    }

    public List<CatalogDtos.Named> categories() {
        return categories.findAll(Sort.by("id")).stream().map(c -> new CatalogDtos.Named(c.getId(), c.getName()))
                .toList();
    }

    public List<CatalogDtos.Named> brands(Long categoryId) {
        List<Brand> list = categoryId == null ? brands.findAll(Sort.by("name")) : brands.findByCategory(categoryId);
        return list.stream().map(b -> new CatalogDtos.Named(b.getId(), b.getName())).toList();
    }

    public ProductDetail detail(Long id) {
        Product p = visible(id);
        List<Product> related = products.findRelated(p.getId(), p.getCategory().getId(), p.getWriter().getId(),
                PageRequest.of(0, 6));
        return mapper.detail(p, related);
    }

    public List<ProductView> related(Long id) {
        Product p = visible(id);
        return mapper.views(products.findRelated(p.getId(), p.getCategory().getId(), p.getWriter().getId(),
                PageRequest.of(0, 6)));
    }

    public WriterView writer(Long id) {
        Writer w = writers.findById(id).orElseThrow(() -> ApiException.notFound("Writer not found"));
        List<Product> books = products.findByWriterIdOrderByCopiesSoldDesc(id).stream()
                .filter(p -> p.isActive() || CurrentUser.isAdmin()).toList();
        return new WriterView(w.getId(), w.getName(), w.getBio(), mapper.views(books));
    }

    // ---- home and recommendations ----

    public HomeView home() {
        return new HomeView(recommendations(), mapper.views(bestsellers()),
                mapper.views(products.findByActiveTrueOrderByCreatedAtDescIdDesc(PageRequest.of(0, HOME_SIZE))));
    }

    /** Based on the books, writers and categories the user bought; bestsellers for guests or no history. */
    public List<ProductView> recommendations() {
        AuthUser user = CurrentUser.orNull();
        if (user == null) {
            return mapper.views(bestsellers());
        }
        List<PurchasedProduct> history = orders.findPurchasedByUser(user.id());
        if (history.isEmpty()) {
            return mapper.views(bestsellers());
        }
        Set<Long> bought = history.stream().map(PurchasedProduct::getProductId).collect(Collectors.toSet());
        List<Product> bookshelf = products.findAllById(bought);
        Set<Long> categoryIds = bookshelf.stream().map(p -> p.getCategory().getId()).collect(Collectors.toSet());
        Set<Long> brandIds = bookshelf.stream().map(p -> p.getBrand().getId()).collect(Collectors.toSet());
        Set<Long> writerIds = bookshelf.stream().map(p -> p.getWriter().getId()).collect(Collectors.toSet());
        List<Product> picks = products.findRecommended(orNone(bought), orNone(categoryIds), orNone(brandIds),
                orNone(writerIds), PageRequest.of(0, HOME_SIZE));
        return picks.isEmpty() ? mapper.views(bestsellers()) : mapper.views(picks);
    }

    private List<Product> bestsellers() {
        return products.findByActiveTrueOrderByCopiesSoldDescIdAsc(PageRequest.of(0, HOME_SIZE));
    }

    /** JPQL "in ()" with an empty collection is invalid on some databases. */
    private static Set<Long> orNone(Set<Long> ids) {
        return ids.isEmpty() ? Set.of(-1L) : ids;
    }

    // ---- reviews ----

    public List<ReviewView> reviews(Long productId) {
        visible(productId);
        return reviews.findByProductIdOrderByCreatedAtDesc(productId).stream().map(CatalogService::view).toList();
    }

    @Transactional
    public ReviewView review(Long productId, ReviewRequest req) {
        AuthUser auth = CurrentUser.require();
        Product p = visible(productId);
        User user = users.findById(auth.id()).orElseThrow(() -> ApiException.unauthorized("User not found"));
        Review r = reviews.findByProductIdAndUserId(productId, auth.id()).orElseGet(Review::new);
        r.setProductId(productId);
        r.setUserId(auth.id());
        r.setUserName(user.getFirstName() + " " + user.getLastName());
        r.setRating(req.rating());
        r.setComment(req.comment());
        r.setCreatedAt(java.time.Instant.now());
        r = reviews.saveAndFlush(r);

        List<Review> all = reviews.findByProductId(productId);
        double avg = all.stream().mapToInt(Review::getRating).average().orElse(0);
        p.setRatingAvg(BigDecimal.valueOf(avg).setScale(2, RoundingMode.HALF_UP));
        p.setRatingCount(all.size());
        return view(r);
    }

    private static ReviewView view(Review r) {
        return new ReviewView(r.getId(), r.getProductId(), r.getUserName(), r.getRating(), r.getComment(),
                r.getCreatedAt());
    }

    // ---- wishlist ----

    public List<ProductView> wishlist() {
        Long uid = CurrentUser.require().id();
        List<Long> ids = wishlist.findByUserIdOrderByIdDesc(uid).stream().map(WishlistItem::getProductId).toList();
        return ids.stream().map(products::findById).flatMap(java.util.Optional::stream)
                .filter(p -> p.isActive() || CurrentUser.isAdmin()).map(mapper::view).toList();
    }

    @Transactional
    public void addToWishlist(Long productId) {
        Long uid = CurrentUser.require().id();
        visible(productId);
        if (wishlist.findByUserIdAndProductId(uid, productId).isEmpty()) {
            wishlist.save(new WishlistItem(uid, productId));
        }
    }

    @Transactional
    public void removeFromWishlist(Long productId) {
        Long uid = CurrentUser.require().id();
        wishlist.findByUserIdAndProductId(uid, productId).ifPresent(wishlist::delete);
    }

    // ---- admin ----

    @Transactional
    public ProductDetail create(ProductRequest req) {
        Product p = new Product();
        apply(p, req);
        return mapper.detail(products.save(p), List.of());
    }

    @Transactional
    public ProductDetail update(Long id, ProductRequest req) {
        Product p = products.findById(id).orElseThrow(() -> ApiException.notFound("Product not found"));
        apply(p, req);
        return mapper.detail(p, List.of());
    }

    @Transactional
    public void deactivate(Long id) {
        products.findById(id).orElseThrow(() -> ApiException.notFound("Product not found")).setActive(false);
    }

    @Transactional
    public CatalogDtos.Named createCategory(String name) {
        Category c = categories.save(new Category(name.trim()));
        return new CatalogDtos.Named(c.getId(), c.getName());
    }

    @Transactional
    public CatalogDtos.Named createBrand(String name) {
        Brand b = brands.save(new Brand(name.trim()));
        return new CatalogDtos.Named(b.getId(), b.getName());
    }

    @Transactional
    public CatalogDtos.WriterInfo createWriter(String name, String bio) {
        Writer w = writers.save(new Writer(name.trim(), bio));
        return new CatalogDtos.WriterInfo(w.getId(), w.getName(), w.getBio());
    }

    private void apply(Product p, ProductRequest r) {
        p.setTitle(r.title());
        p.setDescription(r.description());
        p.setBackCoverBlurb(r.backCoverBlurb());
        p.setIsbn(r.isbn() == null || r.isbn().isBlank() ? null : r.isbn());
        p.setLanguage(r.language());
        p.setBookFormat(r.format());
        p.setPrice(r.price());
        p.setStock(r.stock() == null ? 0 : r.stock());
        p.setDeliveryDays(r.deliveryDays());
        p.setCoverUrl(r.coverUrl());
        p.setGenreTags(r.genreTags() == null ? null : String.join(",", r.genreTags()));
        p.setActive(r.active() == null || r.active());
        p.setCategory(categories.findById(r.categoryId())
                .orElseThrow(() -> ApiException.badRequest("Unknown categoryId")));
        p.setBrand(brands.findById(r.brandId()).orElseThrow(() -> ApiException.badRequest("Unknown brandId")));
        p.setWriter(writers.findById(r.writerId()).orElseThrow(() -> ApiException.badRequest("Unknown writerId")));
        p.setCatalogId(r.catalogId());
    }

    /** Entitlement: ADMIN sees inactive products, everyone else gets 404 for them. */
    private Product visible(Long id) {
        Product p = products.findById(id).orElseThrow(() -> ApiException.notFound("Product not found"));
        if (!p.isActive() && !CurrentUser.isAdmin()) {
            throw ApiException.notFound("Product not found");
        }
        return p;
    }
}
