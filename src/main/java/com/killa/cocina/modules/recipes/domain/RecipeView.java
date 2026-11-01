package com.killa.cocina.modules.recipes.domain;

import java.time.Instant;

/**
 * Receta con su versión vigente y, si existe, el borrador en curso.
 *
 * @param currentVersion última versión publicada; {@code null} si nunca se publicó
 * @param draft borrador editable; {@code null} si no hay ninguno abierto
 */
public record RecipeView(
        Long id,
        String name,
        String category,
        boolean active,
        RecipeVersionSummary currentVersion,
        RecipeVersionSummary draft,
        Instant createdAt,
        Instant updatedAt) {}
