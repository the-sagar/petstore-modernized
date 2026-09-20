package com.mdb.petstore.catalog.dto;

import java.util.List;

public record CatalogPage<T>(List<T> content, int page, int size, long totalElements,
        long totalPages, boolean hasPrevious, boolean hasNext) {

    public static <T> CatalogPage<T> of(List<T> content, int page, int size, long total) {
        long pages = total / size + (total % size == 0 ? 0 : 1);
        return new CatalogPage<>(List.copyOf(content), page, size, total, pages,
                page > 0, (long) page + 1 < pages);
    }
}
