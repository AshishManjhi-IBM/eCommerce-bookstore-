package com.bookstore.catalog;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BrandRepository extends JpaRepository<Brand, Long> {

    @Query("select distinct p.brand from Product p where p.category.id = :categoryId and p.active = true order by p.brand.name")
    List<Brand> findByCategory(@Param("categoryId") Long categoryId);
}
