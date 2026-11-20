package com.bookstore.store;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface CatalogRepository extends JpaRepository<CatalogEntity, Long> {

    List<CatalogEntity> findByStoreId(Long storeId);
}
