package com.bookstore.payment;

import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.UUID;
import java.util.regex.Pattern;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import com.bookstore.common.ApiException;
import com.bookstore.payment.PaymentDtos.PaymentRequest;

/**
 * Mock payment gateway. Validates the payment details and approves, with no external call.
 * A card number ending in 0002 is always declined, which lets tests exercise the failure path.
 */
@Component
public class MockPaymentGateway {

    private static final Pattern UPI = Pattern.compile("^[\\w.\\-]{2,}@[A-Za-z]{2,}$");
    private static final DateTimeFormatter EXPIRY = DateTimeFormatter.ofPattern("MM/yy");

    /** Result of an approved card or UPI charge. */
    public record Approval(String transactionRef, String cardLast4) {
    }

    public Approval authorize(PaymentRequest req) {
        String last4 = null;
        if (req.method().isCard()) {
            String number = digits(req.cardNumber());
            if (number.length() < 13 || number.length() > 19 || !luhn(number)) {
                throw declined("Invalid card number");
            }
            if (req.cvv() == null || !req.cvv().matches("\\d{3,4}")) {
                throw declined("Invalid CVV");
            }
            if (expired(req.expiry())) {
                throw declined("Card has expired or the expiry date is invalid");
            }
            if (number.endsWith("0002")) {
                throw declined("Card was declined by the bank");
            }
            last4 = number.substring(number.length() - 4);
        } else if (req.method() == PaymentMethod.UPI) {
            if (req.upiId() == null || !UPI.matcher(req.upiId().trim()).matches()) {
                throw declined("Invalid UPI id");
            }
        }
        return new Approval(newRef(), last4);
    }

    public String newRef() {
        return "TXN-" + UUID.randomUUID().toString().replace("-", "").substring(0, 16).toUpperCase();
    }

    private static ApiException declined(String message) {
        return new ApiException(HttpStatus.PAYMENT_REQUIRED, "Payment declined: " + message);
    }

    private static String digits(String s) {
        return s == null ? "" : s.replaceAll("[\\s-]", "");
    }

    private static boolean luhn(String number) {
        if (!number.matches("\\d+")) {
            return false;
        }
        int sum = 0;
        boolean alt = false;
        for (int i = number.length() - 1; i >= 0; i--) {
            int d = number.charAt(i) - '0';
            if (alt) {
                d *= 2;
                if (d > 9) {
                    d -= 9;
                }
            }
            sum += d;
            alt = !alt;
        }
        return sum % 10 == 0;
    }

    private static boolean expired(String expiry) {
        if (expiry == null) {
            return true;
        }
        try {
            return YearMonth.parse(expiry.trim(), EXPIRY).isBefore(YearMonth.now());
        } catch (DateTimeParseException ex) {
            return true;
        }
    }
}
