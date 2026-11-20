package com.bookstore.order;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import com.bookstore.catalog.CatalogDtos.ProductView;
import com.bookstore.member.MemberDtos.AddressRequest;
import com.bookstore.payment.PaymentDtos.PaymentRequest;
import com.bookstore.payment.PaymentDtos.PaymentView;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public final class OrderDtos {

    private OrderDtos() {
    }

    // ---- basket ----

    public record BasketItemRequest(@NotNull Long productId, @Min(1) Integer quantity) {
    }

    public record QuantityRequest(@NotNull @Min(0) Integer quantity) {
    }

    public record BasketItemView(Long productId, String title, String coverUrl, BigDecimal price, int quantity,
            BigDecimal lineTotal, LocalDate deliveryDate) {
    }

    /** itemCount feeds the cart badge. guestToken is only set for guest baskets. */
    public record BasketView(String guestToken, int itemCount, List<BasketItemView> items, BigDecimal subtotal,
            List<ProductView> recommendations) {
    }

    // ---- checkout ----

    public record QuoteRequest(String couponCode, @Min(0) Integer giftPointsToRedeem, String state) {
    }

    public record Quote(BigDecimal subtotal, BigDecimal tax, BigDecimal deliveryCharge, BigDecimal discount,
            int giftPointsRedeemed, BigDecimal giftPointsDiscount, BigDecimal totalAmount, int giftPointsToEarn,
            LocalDate estimatedDelivery) {
    }

    /** Provide addressId (Use Saved Address) or an inline address. */
    public record CheckoutRequest(Long addressId, @Valid AddressRequest address, Boolean saveAddress,
            String couponCode, @Min(0) Integer giftPointsToRedeem, @NotNull @Valid PaymentRequest payment) {
    }

    // ---- orders ----

    public record OrderItemView(Long productId, String title, int quantity, BigDecimal unitPrice,
            LocalDate deliveryDate) {
    }

    public record OrderView(Long id, OrderStatus status, String message, Instant placedAt,
            Instant cancellableUntil, boolean cancellable, List<OrderItemView> items, BigDecimal subtotal,
            BigDecimal tax, BigDecimal deliveryCharge, BigDecimal discount, String couponCode,
            int giftPointsRedeemed, BigDecimal giftPointsDiscount, int giftPointsEarned, BigDecimal totalAmount,
            LocalDate estimatedDelivery, AddressRequest shippingAddress, PaymentView payment) {
    }

    public record StatusRequest(@NotNull OrderStatus status) {
    }

    // ---- coupons ----

    public record CouponRequest(@NotBlank String code, @NotNull @Min(1) @Max(100) Integer percentOff,
            Instant expiresAt) {
    }

    public record CouponView(Long id, String code, int percentOff, boolean active, Instant expiresAt) {
    }
}
