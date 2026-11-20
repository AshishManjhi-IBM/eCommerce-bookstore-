package com.bookstore.catalog;

import java.util.Collection;
import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

public interface ProductRepository extends JpaRepository<Product, Long>, JpaSpecificationExecutor<Product> {

    List<Product> findByWriterIdOrderByCopiesSoldDesc(Long writerId);

    List<Product> findByActiveTrueOrderByCopiesSoldDescIdAsc(Pageable pageable);

    List<Product> findByActiveTrueOrderByCreatedAtDescIdDesc(Pageable pageable);

    @Query("select p from Product p where p.active = true and p.id <> :id "
            + "and (p.category.id = :categoryId or p.writer.id = :writerId) "
            + "order by case when p.writer.id = :writerId then 0 else 1 end, p.copiesSold desc, p.id")
    List<Product> findRelated(@Param("id") Long id, @Param("categoryId") Long categoryId,
            @Param("writerId") Long writerId, Pageable pageable);

    /** Active books sharing category, brand or writer with past purchases, minus the purchased ones. */
    @Query("select p from Product p where p.active = true and p.id not in :excluded "
            + "and (p.category.id in :categoryIds or p.brand.id in :brandIds or p.writer.id in :writerIds) "
            + "order by p.copiesSold desc, p.id")
    List<Product> findRecommended(@Param("excluded") Collection<Long> excluded,
            @Param("categoryIds") Collection<Long> categoryIds, @Param("brandIds") Collection<Long> brandIds,
            @Param("writerIds") Collection<Long> writerIds, Pageable pageable);

    /** Row locks in id order, used while placing or cancelling orders. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Product p where p.id in :ids order by p.id")
    List<Product> lockAllByIdIn(@Param("ids") Collection<Long> ids);
}
