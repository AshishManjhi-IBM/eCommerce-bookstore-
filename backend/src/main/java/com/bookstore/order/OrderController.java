package com.bookstore.order;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.bookstore.order.OrderDtos.BasketView;
import com.bookstore.order.OrderDtos.CheckoutRequest;
import com.bookstore.order.OrderDtos.CouponRequest;
import com.bookstore.order.OrderDtos.CouponView;
import com.bookstore.order.OrderDtos.OrderView;
import com.bookstore.order.OrderDtos.Quote;
import com.bookstore.order.OrderDtos.QuoteRequest;
import com.bookstore.order.OrderDtos.StatusRequest;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api")
public class OrderController {

    private final OrderService orders;

    public OrderController(OrderService orders) {
        this.orders = orders;
    }

    @PostMapping("/checkout/quote")
    public Quote quote(@Valid @RequestBody QuoteRequest req) {
        return orders.quote(req);
    }

    @PostMapping("/orders")
    @ResponseStatus(HttpStatus.CREATED)
    public OrderView checkout(@Valid @RequestBody CheckoutRequest req) {
        return orders.checkout(req);
    }

    @GetMapping("/orders")
    public List<OrderView> history() {
        return orders.history();
    }

    @GetMapping("/orders/{id}")
    public OrderView get(@PathVariable Long id) {
        return orders.get(id);
    }

    @PostMapping("/orders/{id}/cancel")
    public OrderView cancel(@PathVariable Long id) {
        return orders.cancel(id);
    }

    @PostMapping("/orders/{id}/return")
    public OrderView requestReturn(@PathVariable Long id) {
        return orders.requestReturn(id);
    }

    @PostMapping("/orders/{id}/buy-again")
    public BasketView buyAgain(@PathVariable Long id) {
        return orders.buyAgain(id);
    }

    @PutMapping("/orders/{id}/status")
    public OrderView updateStatus(@PathVariable Long id, @Valid @RequestBody StatusRequest req) {
        return orders.updateStatus(id, req.status());
    }

    @PostMapping("/coupons")
    @ResponseStatus(HttpStatus.CREATED)
    public CouponView createCoupon(@Valid @RequestBody CouponRequest req) {
        return orders.createCoupon(req);
    }
}
