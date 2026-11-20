package com.bookstore.order;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.bookstore.order.OrderDtos.BasketItemRequest;
import com.bookstore.order.OrderDtos.BasketView;
import com.bookstore.order.OrderDtos.QuantityRequest;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/basket")
public class BasketController {

    private static final String GUEST = "X-Guest-Token";

    private final BasketService basket;

    public BasketController(BasketService basket) {
        this.basket = basket;
    }

    @GetMapping
    public BasketView get(@RequestHeader(name = GUEST, required = false) String guestToken) {
        return basket.get(guestToken);
    }

    @PostMapping("/items")
    public BasketView add(@RequestHeader(name = GUEST, required = false) String guestToken,
            @Valid @RequestBody BasketItemRequest req) {
        return basket.add(guestToken, req.productId(), req.quantity() == null ? 1 : req.quantity());
    }

    @PutMapping("/items/{productId}")
    public BasketView setQuantity(@RequestHeader(name = GUEST, required = false) String guestToken,
            @PathVariable Long productId, @Valid @RequestBody QuantityRequest req) {
        return basket.setQuantity(guestToken, productId, req.quantity());
    }

    @DeleteMapping("/items/{productId}")
    public BasketView remove(@RequestHeader(name = GUEST, required = false) String guestToken,
            @PathVariable Long productId) {
        return basket.remove(guestToken, productId);
    }
}
