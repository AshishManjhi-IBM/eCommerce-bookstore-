package com.bookstore.catalog;

import java.util.Arrays;
import java.util.List;

import org.springframework.stereotype.Component;

import com.bookstore.catalog.CatalogDtos.Named;
import com.bookstore.catalog.CatalogDtos.ProductDetail;
import com.bookstore.catalog.CatalogDtos.ProductView;
import com.bookstore.catalog.CatalogDtos.WriterInfo;
import com.bookstore.shipping.ShippingService;

@Component
public class ProductMapper {

    private final ShippingService shipping;

    public ProductMapper(ShippingService shipping) {
        this.shipping = shipping;
    }

    public ProductView view(Product p) {
        return new ProductView(p.getId(), p.getTitle(), p.getDescription(), p.getWriter().getId(),
                p.getWriter().getName(), p.getBookFormat(), tags(p), p.getPrice(), p.getCoverUrl(), p.getRatingAvg(),
                p.getRatingCount(), p.getStock() > 0, p.isActive(),
                shipping.deliveryDate(p.getDeliveryDays(), null));
    }

    public List<ProductView> views(List<Product> products) {
        return products.stream().map(this::view).toList();
    }

    public ProductDetail detail(Product p, List<Product> related) {
        return new ProductDetail(p.getId(), p.getTitle(), p.getDescription(), p.getWriter().getId(),
                p.getWriter().getName(), p.getBookFormat(), tags(p), p.getPrice(), p.getCoverUrl(), p.getRatingAvg(),
                p.getRatingCount(), p.getStock() > 0, p.isActive(), shipping.deliveryDate(p.getDeliveryDays(), null),
                p.getBackCoverBlurb(), p.getBrand().getName(), p.getIsbn(), p.getLanguage(), p.getCopiesSold(),
                p.getStock(), new Named(p.getCategory().getId(), p.getCategory().getName()),
                new WriterInfo(p.getWriter().getId(), p.getWriter().getName(), p.getWriter().getBio()),
                views(related));
    }

    static List<String> tags(Product p) {
        if (p.getGenreTags() == null || p.getGenreTags().isBlank()) {
            return List.of();
        }
        return Arrays.stream(p.getGenreTags().split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList();
    }
}
