package com.bookstore.order;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface BasketItemRepository extends JpaRepository<BasketItem, Long> {

    List<BasketItem> findByBasketIdOrderByIdAsc(Long basketId);

    Optional<BasketItem> findByBasketIdAndProductId(Long basketId, Long productId);
}
