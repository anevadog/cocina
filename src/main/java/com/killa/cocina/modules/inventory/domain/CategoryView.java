package com.killa.cocina.modules.inventory.domain;

import java.time.Instant;

/** Categoría expuesta por la API. */
public record CategoryView(Long id, String name, Instant createdAt, Instant updatedAt) {

    public static CategoryView from(Category category) {
        return new CategoryView(category.id(), category.name(), category.createdAt(),
                category.updatedAt());
    }
}
