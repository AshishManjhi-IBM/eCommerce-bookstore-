package com.bookstore.shipping;

import java.time.LocalDate;
import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.bookstore.catalog.Product;
import com.bookstore.catalog.ProductRepository;
import com.bookstore.common.ApiException;

import java.math.BigDecimal;

@RestController
@RequestMapping("/api/shipping")
public class ShippingController {

    public record RateView(String region, BigDecimal charge, int deliveryDays) {
    }

    public record EstimateView(LocalDate deliveryDate, BigDecimal charge) {
    }

    private final ShippingRateRepository rates;
    private final ShippingService shipping;
    private final ProductRepository products;

    public ShippingController(ShippingRateRepository rates, ShippingService shipping, ProductRepository products) {
        this.rates = rates;
        this.shipping = shipping;
        this.products = products;
    }

    @GetMapping("/rates")
    public List<RateView> rates() {
        return rates.findAll().stream().map(r -> new RateView(r.getRegion(), r.getCharge(), r.getDeliveryDays()))
                .toList();
    }

    @GetMapping("/estimate")
    public EstimateView estimate(@RequestParam Long productId, @RequestParam(required = false) String state) {
        Product p = products.findById(productId).filter(Product::isActive)
                .orElseThrow(() -> ApiException.notFound("Product not found"));
        return new EstimateView(shipping.deliveryDate(p.getDeliveryDays(), state),
                shipping.charge(p.getPrice(), state));
    }
}
