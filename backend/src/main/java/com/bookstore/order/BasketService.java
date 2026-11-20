package com.bookstore.order;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.bookstore.catalog.CatalogService;
import com.bookstore.catalog.Product;
import com.bookstore.catalog.ProductRepository;
import com.bookstore.common.ApiException;
import com.bookstore.order.OrderDtos.BasketItemView;
import com.bookstore.order.OrderDtos.BasketView;
import com.bookstore.security.AuthUser;
import com.bookstore.security.CurrentUser;
import com.bookstore.shipping.ShippingService;

/** Basket for guests (X-Guest-Token) and logged in users. */
@Service
public class BasketService {

    private final BasketRepository baskets;
    private final BasketItemRepository items;
    private final ProductRepository products;
    private final ShippingService shipping;
    private final CatalogService catalog;

    public BasketService(BasketRepository baskets, BasketItemRepository items, ProductRepository products,
            ShippingService shipping, CatalogService catalog) {
        this.baskets = baskets;
        this.items = items;
        this.products = products;
        this.shipping = shipping;
        this.catalog = catalog;
    }

    @Transactional(readOnly = true)
    public BasketView get(String guestToken) {
        return view(resolve(guestToken, false));
    }

    @Transactional
    public BasketView add(String guestToken, Long productId, int quantity) {
        Basket basket = resolve(guestToken, true);
        addTo(basket, requireActive(productId), quantity, false);
        return view(basket);
    }

    @Transactional
    public BasketView setQuantity(String guestToken, Long productId, int quantity) {
        Basket basket = resolve(guestToken, false);
        if (basket == null) {
            throw ApiException.notFound("Item not in basket");
        }
        BasketItem item = items.findByBasketIdAndProductId(basket.getId(), productId)
                .orElseThrow(() -> ApiException.notFound("Item not in basket"));
        if (quantity == 0) {
            items.delete(item);
        } else {
            checkStock(requireActive(productId), quantity);
            item.setQuantity(quantity);
        }
        return view(basket);
    }

    @Transactional
    public BasketView remove(String guestToken, Long productId) {
        Basket basket = resolve(guestToken, false);
        if (basket != null) {
            items.findByBasketIdAndProductId(basket.getId(), productId).ifPresent(items::delete);
        }
        return view(basket);
    }

    /** Moves the guest basket into the user basket on login. */
    @Transactional
    public void mergeGuestBasket(Long userId, String guestToken) {
        if (guestToken == null || guestToken.isBlank()) {
            return;
        }
        Basket guest = baskets.findByGuestToken(guestToken).orElse(null);
        if (guest == null) {
            return;
        }
        Basket mine = forUser(userId);
        for (BasketItem gi : items.findByBasketIdOrderByIdAsc(guest.getId())) {
            products.findById(gi.getProductId()).filter(Product::isActive)
                    .ifPresent(p -> addTo(mine, p, gi.getQuantity(), true));
            items.delete(gi);
        }
        baskets.delete(guest);
    }

    /** Used by "Buy again": adds what is still available, silently skipping the rest. */
    @Transactional
    public BasketView addAvailable(Long userId, Map<Long, Integer> quantities) {
        Basket basket = forUser(userId);
        quantities.forEach((productId, qty) -> products.findById(productId).filter(Product::isActive)
                .filter(p -> p.getStock() > 0).ifPresent(p -> addTo(basket, p, qty, true)));
        return view(basket);
    }

    @Transactional
    public Basket forUser(Long userId) {
        return baskets.findByUserId(userId).orElseGet(() -> {
            Basket b = new Basket();
            b.setUserId(userId);
            return baskets.save(b);
        });
    }

    public List<BasketItem> itemsOf(Basket basket) {
        return items.findByBasketIdOrderByIdAsc(basket.getId());
    }

    @Transactional
    public void clear(Basket basket) {
        items.deleteAll(items.findByBasketIdOrderByIdAsc(basket.getId()));
    }

    // ---- internals ----

    private Basket resolve(String guestToken, boolean create) {
        AuthUser user = CurrentUser.orNull();
        if (user != null) {
            return create ? forUser(user.id()) : baskets.findByUserId(user.id()).orElse(null);
        }
        if (guestToken != null && !guestToken.isBlank()) {
            var found = baskets.findByGuestToken(guestToken);
            if (found.isPresent()) {
                return found.get();
            }
        }
        if (!create) {
            return null;
        }
        Basket b = new Basket();
        b.setGuestToken(UUID.randomUUID().toString());
        b.setCreatedAt(Instant.now());
        return baskets.save(b);
    }

    private void addTo(Basket basket, Product product, int quantity, boolean capToStock) {
        BasketItem item = items.findByBasketIdAndProductId(basket.getId(), product.getId()).orElse(null);
        int wanted = (item == null ? 0 : item.getQuantity()) + quantity;
        if (capToStock) {
            wanted = Math.min(wanted, product.getStock());
            if (wanted <= 0) {
                return;
            }
        } else {
            checkStock(product, wanted);
        }
        if (item == null) {
            items.save(new BasketItem(basket.getId(), product.getId(), wanted));
        } else {
            item.setQuantity(wanted);
        }
    }

    private static void checkStock(Product product, int quantity) {
        if (quantity > product.getStock()) {
            throw ApiException.badRequest("Only " + product.getStock() + " copies of '" + product.getTitle()
                    + "' are available");
        }
    }

    private Product requireActive(Long productId) {
        return products.findById(productId).filter(Product::isActive)
                .orElseThrow(() -> ApiException.notFound("Product not found"));
    }

    private BasketView view(Basket basket) {
        List<BasketItemView> views = List.of();
        BigDecimal subtotal = BigDecimal.ZERO;
        int count = 0;
        if (basket != null) {
            List<BasketItem> rows = items.findByBasketIdOrderByIdAsc(basket.getId());
            Map<Long, Product> byId = products.findAllById(rows.stream().map(BasketItem::getProductId).toList())
                    .stream().collect(Collectors.toMap(Product::getId, Function.identity()));
            views = rows.stream().map(r -> {
                Product p = byId.get(r.getProductId());
                BigDecimal line = p.getPrice().multiply(BigDecimal.valueOf(r.getQuantity()));
                return new BasketItemView(p.getId(), p.getTitle(), p.getCoverUrl(), p.getPrice(), r.getQuantity(),
                        line, shipping.deliveryDate(p.getDeliveryDays(), null));
            }).toList();
            subtotal = views.stream().map(BasketItemView::lineTotal).reduce(BigDecimal.ZERO, BigDecimal::add);
            count = views.stream().mapToInt(BasketItemView::quantity).sum();
        }
        String token = basket == null ? null : basket.getGuestToken();
        return new BasketView(token, count, views, subtotal, catalog.recommendations());
    }
}
