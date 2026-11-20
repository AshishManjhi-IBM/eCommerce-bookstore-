package com.bookstore.store;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface StorePolicyRepository extends JpaRepository<StorePolicy, Long> {

    List<StorePolicy> findByStoreId(Long storeId);
}
