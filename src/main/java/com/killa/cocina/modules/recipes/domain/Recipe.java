package com.killa.cocina.modules.recipes.domain;

import java.time.Instant;

/**
 * Cabecera de una receta (REC-01).
 *
 * <p>{@code currentVersionId} apunta a la última versión publicada; mientras la receta solo tenga
 * borradores vale {@code null}. La receta nunca se borra: se archiva con {@code active=false}.</p>
 */
public record Recipe(
        Long id,
        long establishmentId,
        String name,
        String category,
        boolean active,
        Long currentVersionId,
        Instant createdAt,
        Instant updatedAt) {

    public static Recipe create(long establishmentId, RecipeRegistration registration, Instant now) {
        return new Recipe(null, establishmentId, registration.name(), registration.category(), true, null, now, now);
    }

    /** Edición parcial: los valores ya vienen normalizados por {@link RecipeModification}. */
    public Recipe update(String name, String category, Boolean active, Instant now) {
        return new Recipe(
                id,
                establishmentId,
                name == null ? this.name : name,
                category,
                active == null ? this.active : active,
                currentVersionId,
                createdAt,
                now);
    }

    public Recipe withCurrentVersion(Long versionId, Instant now) {
        return new Recipe(id, establishmentId, name, category, active, versionId, createdAt, now);
    }
}
