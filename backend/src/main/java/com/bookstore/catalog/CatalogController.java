package com.bookstore.catalog;

import java.math.BigDecimal;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.bookstore.catalog.CatalogDtos.HomeView;
import com.bookstore.catalog.CatalogDtos.NameRequest;
import com.bookstore.catalog.CatalogDtos.Named;
import com.bookstore.catalog.CatalogDtos.ProductDetail;
import com.bookstore.catalog.CatalogDtos.ProductPage;
import com.bookstore.catalog.CatalogDtos.ProductRequest;
import com.bookstore.catalog.CatalogDtos.ProductView;
import com.bookstore.catalog.CatalogDtos.ReviewRequest;
import com.bookstore.catalog.CatalogDtos.ReviewView;
import com.bookstore.catalog.CatalogDtos.WriterInfo;
import com.bookstore.catalog.CatalogDtos.WriterRequest;
import com.bookstore.catalog.CatalogDtos.WriterView;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api")
public class CatalogController {

    private final CatalogService catalog;

    public CatalogController(CatalogService catalog) {
        this.catalog = catalog;
    }

    @GetMapping("/categories")
    public List<Named> categories() {
        return catalog.categories();
    }

    @PostMapping("/categories")
    @ResponseStatus(HttpStatus.CREATED)
    public Named createCategory(@Valid @RequestBody NameRequest req) {
        return catalog.createCategory(req.name());
    }

    @GetMapping("/brands")
    public List<Named> brands(@RequestParam(required = false) Long categoryId) {
        return catalog.brands(categoryId);
    }

    @PostMapping("/brands")
    @ResponseStatus(HttpStatus.CREATED)
    public Named createBrand(@Valid @RequestBody NameRequest req) {
        return catalog.createBrand(req.name());
    }

    @PostMapping("/writers")
    @ResponseStatus(HttpStatus.CREATED)
    public WriterInfo createWriter(@Valid @RequestBody WriterRequest req) {
        return catalog.createWriter(req.name(), req.bio());
    }

    @GetMapping("/writers/{id}")
    public WriterView writer(@PathVariable Long id) {
        return catalog.writer(id);
    }

    @GetMapping("/products")
    public ProductPage search(@RequestParam(required = false) String q,
            @RequestParam(required = false) Long categoryId, @RequestParam(required = false) Long brandId,
            @RequestParam(required = false) String language, @RequestParam(required = false) BookFormat format,
            @RequestParam(required = false) BigDecimal minPrice, @RequestParam(required = false) BigDecimal maxPrice,
            @RequestParam(defaultValue = "relevance") String sort, @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return catalog.search(q, categoryId, brandId, language, format, minPrice, maxPrice, sort, page, size);
    }

    @PostMapping("/products")
    @ResponseStatus(HttpStatus.CREATED)
    public ProductDetail create(@Valid @RequestBody ProductRequest req) {
        return catalog.create(req);
    }

    @GetMapping("/products/{id}")
    public ProductDetail detail(@PathVariable Long id) {
        return catalog.detail(id);
    }

    @PutMapping("/products/{id}")
    public ProductDetail update(@PathVariable Long id, @Valid @RequestBody ProductRequest req) {
        return catalog.update(id, req);
    }

    @DeleteMapping("/products/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deactivate(@PathVariable Long id) {
        catalog.deactivate(id);
    }

    @GetMapping("/products/{id}/related")
    public List<ProductView> related(@PathVariable Long id) {
        return catalog.related(id);
    }

    @GetMapping("/products/{id}/reviews")
    public List<ReviewView> reviews(@PathVariable Long id) {
        return catalog.reviews(id);
    }

    @PostMapping("/products/{id}/reviews")
    @ResponseStatus(HttpStatus.CREATED)
    public ReviewView review(@PathVariable Long id, @Valid @RequestBody ReviewRequest req) {
        return catalog.review(id, req);
    }

    @GetMapping("/home")
    public HomeView home() {
        return catalog.home();
    }

    @GetMapping("/recommendations")
    public List<ProductView> recommendations() {
        return catalog.recommendations();
    }

    @GetMapping("/wishlist")
    public List<ProductView> wishlist() {
        return catalog.wishlist();
    }

    @PostMapping("/wishlist/{productId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void addToWishlist(@PathVariable Long productId) {
        catalog.addToWishlist(productId);
    }

    @DeleteMapping("/wishlist/{productId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeFromWishlist(@PathVariable Long productId) {
        catalog.removeFromWishlist(productId);
    }
}
