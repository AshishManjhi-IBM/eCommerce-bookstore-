package com.bookstore.payment;

import java.math.BigDecimal;
import java.time.Instant;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import com.bookstore.common.ApiException;
import com.bookstore.member.User;
import com.bookstore.payment.MockPaymentGateway.Approval;
import com.bookstore.payment.PaymentDtos.PaymentRequest;

/** Payment service: charge through the mock gateway or wallet, and refund. Runs inside the caller's transaction. */
@Service
public class PaymentService {

    private final PaymentRepository payments;
    private final MockPaymentGateway gateway;

    public PaymentService(PaymentRepository payments, MockPaymentGateway gateway) {
        this.payments = payments;
        this.gateway = gateway;
    }

    /** Throws 402 when declined, which rolls back the checkout transaction. */
    public Payment charge(User user, Long orderId, BigDecimal amount, PaymentRequest req) {
        String ref;
        String last4 = null;
        if (req.method() == PaymentMethod.WALLET) {
            if (user.getWalletBalance().compareTo(amount) < 0) {
                throw new ApiException(HttpStatus.PAYMENT_REQUIRED, "Payment declined: insufficient wallet balance");
            }
            user.setWalletBalance(user.getWalletBalance().subtract(amount));
            ref = gateway.newRef();
        } else {
            Approval approval = gateway.authorize(req);
            ref = approval.transactionRef();
            last4 = approval.cardLast4();
        }
        Payment p = new Payment();
        p.setOrderId(orderId);
        p.setMethod(req.method());
        p.setStatus(PaymentStatus.SUCCESS);
        p.setAmount(amount);
        p.setCardLast4(last4);
        p.setTransactionRef(ref);
        return payments.save(p);
    }

    public Payment forOrder(Long orderId) {
        return payments.findByOrderId(orderId).orElse(null);
    }

    /** Marks the payment refunded; wallet payments go back to the wallet. */
    public void refund(Payment payment, User user) {
        if (payment == null || payment.getStatus() != PaymentStatus.SUCCESS) {
            return;
        }
        payment.setStatus(PaymentStatus.REFUNDED);
        payment.setRefundedAt(Instant.now());
        if (payment.getMethod() == PaymentMethod.WALLET) {
            user.setWalletBalance(user.getWalletBalance().add(payment.getAmount()));
        }
    }
}
