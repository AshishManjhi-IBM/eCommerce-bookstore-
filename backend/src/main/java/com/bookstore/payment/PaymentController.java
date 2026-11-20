package com.bookstore.payment;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.bookstore.common.ApiException;
import com.bookstore.order.OrderRepository;
import com.bookstore.payment.PaymentDtos.PaymentView;
import com.bookstore.security.CurrentUser;

@RestController
@RequestMapping("/api/payments")
public class PaymentController {

    private final PaymentService payments;
    private final OrderRepository orders;

    public PaymentController(PaymentService payments, OrderRepository orders) {
        this.payments = payments;
        this.orders = orders;
    }

    @GetMapping("/order/{orderId}")
    public PaymentView forOrder(@PathVariable Long orderId) {
        Long uid = CurrentUser.require().id();
        orders.findByIdAndUserId(orderId, uid).orElseThrow(() -> ApiException.notFound("Order not found"));
        Payment p = payments.forOrder(orderId);
        if (p == null) {
            throw ApiException.notFound("Payment not found");
        }
        return PaymentView.of(p);
    }
}
