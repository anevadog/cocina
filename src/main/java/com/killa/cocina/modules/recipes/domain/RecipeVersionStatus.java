package com.killa.cocina.modules.recipes.domain;

/**
 * Ciclo de vida de una versión de receta (REC-05).
 *
 * <p>Solo {@link #DRAFT} se edita en sitio. Al publicar pasa a {@link #PUBLISHED} y queda inmutable:
 * cualquier cambio crea otra versión. {@link #ARCHIVED} reserva la retirada de una versión ya
 * publicada sin borrar su historial.</p>
 */
public enum RecipeVersionStatus {
    DRAFT,
    PUBLISHED,
    ARCHIVED;

    public boolean isDraft() {
        return this == DRAFT;
    }
}
