package com.bookstore.catalog;

import java.math.BigDecimal;
import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "products")
@Getter
@Setter
@NoArgsConstructor
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String title;
    private String description;
    private String backCoverBlurb;
    private String isbn;
    private String language;

    @Enumerated(EnumType.STRING)
    @Column(name = "book_format")
    private BookFormat bookFormat;

    private BigDecimal price;
    private int stock;

    /** Null means "use the region default". */
    private Integer deliveryDays;

    private String coverUrl;

    /** Comma separated genre tags. */
    private String genreTags;

    private boolean active = true;
    private int copiesSold;
    private BigDecimal ratingAvg = BigDecimal.ZERO;
    private int ratingCount;
    private Instant createdAt = Instant.now();

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "category_id")
    private Category category;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "brand_id")
    private Brand brand;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "writer_id")
    private Writer writer;

    private Long catalogId;
}
