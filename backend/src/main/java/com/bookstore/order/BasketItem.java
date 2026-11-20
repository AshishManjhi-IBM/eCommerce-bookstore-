package com.bookstore.order;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "basket_items")
@Getter
@Setter
@NoArgsConstructor
public class BasketItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long basketId;
    private Long productId;
    private int quantity;

    public BasketItem(Long basketId, Long productId, int quantity) {
        this.basketId = basketId;
        this.productId = productId;
        this.quantity = quantity;
    }
}
