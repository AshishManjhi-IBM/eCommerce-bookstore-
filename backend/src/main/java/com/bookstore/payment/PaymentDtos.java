package com.bookstore.payment;

import java.math.BigDecimal;

import jakarta.validation.constraints.NotNull;

public final class PaymentDtos {

    private PaymentDtos() {
    }

    /**
     * CREDIT_CARD / DEBIT_CARD need cardNumber, expiry (MM/YY) and cvv. UPI needs upiId. WALLET needs enough balance.
     * The card number and CVV are only validated, never stored or echoed back.
     */
    public record PaymentRequest(@NotNull PaymentMethod method, String cardHolder, String cardNumber, String expiry,
            String cvv, String upiId) {

        @Override
        public String toString() {
            return "PaymentRequest[method=" + method + "]";
        }
    }

    public record PaymentView(Long id, PaymentMethod method, PaymentStatus status, BigDecimal amount,
            String cardLast4, String transactionRef) {

        public static PaymentView of(Payment p) {
            return new PaymentView(p.getId(), p.getMethod(), p.getStatus(), p.getAmount(), p.getCardLast4(),
                    p.getTransactionRef());
        }
    }
}
