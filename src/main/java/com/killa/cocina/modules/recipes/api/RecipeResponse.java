package com.killa.cocina.modules.recipes.api;

import com.killa.cocina.modules.recipes.domain.RecipeView;
import java.time.Instant;

/**
 * Receta expuesta por la API (REC-01).
 *
 * @param currentVersion última versión publicada; {@code null} si nunca se publicó
 * @param draft borrador en curso; {@code null} si no hay ninguno
 */
public record RecipeResponse(
        Long id,
        String name,
        String category,
        boolean active,
        RecipeVersionSummaryResponse currentVersion,
        RecipeVersionSummaryResponse draft,
        Instant createdAt,
        Instant updatedAt) {

    public static RecipeResponse from(RecipeView view) {
        return new RecipeResponse(
                view.id(),
                view.name(),
                view.category(),
                view.active(),
                view.currentVersion() == null ? null : RecipeVersionSummaryResponse.from(view.currentVersion()),
                view.draft() == null ? null : RecipeVersionSummaryResponse.from(view.draft()),
                view.createdAt(),
                view.updatedAt());
    }
}
