package com.bookstore.order;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OrderRepository extends JpaRepository<CustomerOrder, Long> {

    /** Product ids of every non-cancelled order of the user. */
    interface PurchasedProduct {
        Long getProductId();
    }

    List<CustomerOrder> findByUserIdOrderByPlacedAtDescIdDesc(Long userId);

    Optional<CustomerOrder> findByIdAndUserId(Long id, Long userId);

    @Query("select distinct i.productId as productId from CustomerOrder o join o.items i "
            + "where o.userId = :userId and o.status <> com.bookstore.order.OrderStatus.CANCELLED")
    List<PurchasedProduct> findPurchasedByUser(@Param("userId") Long userId);
}
