package com.bookstore.order;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Named CustomerOrder because ORDER is a reserved word in JPQL. */
@Entity(name = "CustomerOrder")
@Table(name = "orders")
@Getter
@Setter
@NoArgsConstructor
public class CustomerOrder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long userId;

    @Enumerated(EnumType.STRING)
    private OrderStatus status;

    private BigDecimal subtotal;
    private BigDecimal discount;
    private BigDecimal tax;
    private BigDecimal deliveryCharge;
    private int giftPointsRedeemed;
    private BigDecimal giftPointsDiscount;
    private int giftPointsEarned;
    private BigDecimal totalAmount;
    private String couponCode;

    // delivery address snapshot
    private String firstName;
    private String lastName;
    private String addressLine;
    private String email;
    private String phoneCountryCode;
    private String phone;
    private String city;
    private String pin;
    private String state;
    private String country;

    private LocalDate estimatedDelivery;
    private Instant placedAt;
    private Instant cancellableUntil;

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @JoinColumn(name = "order_id", nullable = false)
    private List<OrderItem> items = new ArrayList<>();

    public boolean isCancellable() {
        return status == OrderStatus.PLACED && Instant.now().isBefore(cancellableUntil);
    }
}
