package com.bookstore.order;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface BasketRepository extends JpaRepository<Basket, Long> {

    Optional<Basket> findByUserId(Long userId);

    Optional<Basket> findByGuestToken(String guestToken);
}
