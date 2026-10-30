package com.killa.cocina.modules.inventory.api;

import com.killa.cocina.modules.inventory.domain.CategoryView;
import java.time.Instant;

/** Categoría de producto expuesta por la API. */
public record CategoryResponse(Long id, String name, Instant createdAt, Instant updatedAt) {

    public static CategoryResponse from(CategoryView view) {
        return new CategoryResponse(view.id(), view.name(), view.createdAt(), view.updatedAt());
    }
}
