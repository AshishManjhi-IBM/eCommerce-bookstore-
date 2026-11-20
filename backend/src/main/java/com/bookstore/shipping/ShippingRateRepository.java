package com.bookstore.shipping;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ShippingRateRepository extends JpaRepository<ShippingRate, Long> {

    Optional<ShippingRate> findByRegionIgnoreCase(String region);
}
