package com.bookstore.catalog;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface WishlistRepository extends JpaRepository<WishlistItem, Long> {

    List<WishlistItem> findByUserIdOrderByIdDesc(Long userId);

    Optional<WishlistItem> findByUserIdAndProductId(Long userId, Long productId);
}
