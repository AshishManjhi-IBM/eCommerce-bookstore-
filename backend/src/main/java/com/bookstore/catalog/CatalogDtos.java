package com.bookstore.catalog;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public final class CatalogDtos {

    private CatalogDtos() {
    }

    public record Named(Long id, String name) {
    }

    public record NameRequest(@NotBlank String name) {
    }

    public record WriterRequest(@NotBlank String name, @Size(max = 2000) String bio) {
    }

    public record ProductRequest(
            @NotBlank String title,
            @Size(max = 1000) String description,
            @Size(max = 4000) String backCoverBlurb,
            String isbn,
            @NotBlank String language,
            @NotNull BookFormat format,
            @NotNull @DecimalMin("0") BigDecimal price,
            @Min(0) Integer stock,
            @Min(0) Integer deliveryDays,
            String coverUrl,
            List<String> genreTags,
            Boolean active,
            @NotNull Long categoryId,
            @NotNull Long brandId,
            @NotNull Long writerId,
            Long catalogId) {
    }

    /** Book card. */
    public record ProductView(Long id, String title, String description, Long authorId, String author,
            BookFormat format, List<String> genreTags, BigDecimal price, String coverUrl, BigDecimal rating,
            int ratingCount, boolean inStock, boolean active, LocalDate deliveryDate) {
    }

    public record WriterInfo(Long id, String name, String bio) {
    }

    /** Product page. */
    public record ProductDetail(Long id, String title, String description, Long authorId, String author,
            BookFormat format, List<String> genreTags, BigDecimal price, String coverUrl, BigDecimal rating,
            int ratingCount, boolean inStock, boolean active, LocalDate deliveryDate, String backCoverBlurb,
            String publisher, String isbn, String language, int copiesSold, int stock, Named category,
            WriterInfo writer, List<ProductView> related) {
    }

    public record ProductPage(List<ProductView> content, int page, int size, long totalElements, int totalPages) {
    }

    public record WriterView(Long id, String name, String bio, List<ProductView> books) {
    }

    public record HomeView(List<ProductView> recommended, List<ProductView> bestsellers,
            List<ProductView> newLaunches) {
    }

    public record ReviewRequest(@NotNull @Min(1) @Max(5) Integer rating,
            @Size(max = 100, message = "must be at most 100 characters") String comment) {
    }

    public record ReviewView(Long id, Long productId, String userName, int rating, String comment,
            Instant createdAt) {
    }
}
