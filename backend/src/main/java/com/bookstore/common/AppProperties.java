package com.bookstore.common;

import java.math.BigDecimal;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app")
public record AppProperties(Jwt jwt, GiftPoints giftPoints, BigDecimal taxRate, Shipping shipping, Seed seed) {

    public record Jwt(String secret, long expirationMinutes) {
    }

    /** valuePerPoint: rupees per point. earnRate: fraction of the amount paid earned as points. */
    public record GiftPoints(BigDecimal valuePerPoint, BigDecimal earnRate) {
    }

    public record Shipping(BigDecimal freeThreshold) {
    }

    public record Seed(boolean enabled, String adminEmail, String adminPassword) {
    }
}
