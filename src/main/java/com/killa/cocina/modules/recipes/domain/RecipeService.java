package com.killa.cocina.modules.recipes.domain;

import com.killa.cocina.shared.pagination.PageResult;
import java.util.List;

/**
 * Casos de uso de recetas (REC-01..05, ECO-01).
 *
 * <p>La implementación transaccional vive en {@code infrastructure}; el API solo ve esta interfaz
 * (ADR-020). El descuento de stock por producción (REC-06) se apoyará en la misma expansión de
 * ingredientes en F5.</p>
 */
public interface RecipeService {

    /** Alta de receta con su primera versión en borrador. */
    RecipeView create(long establishmentId, Long actorId, RecipeRegistration registration);

    /** Edición parcial de la cabecera; archivar no borra el historial. */
    RecipeView update(long establishmentId, Long actorId, long recipeId, RecipeModification modification);

    RecipeView find(long establishmentId, long recipeId);

    PageResult<RecipeView> list(long establishmentId, RecipeFilter filter, int page, int size);

    /** Historial de versiones, de la más reciente a la más antigua. */
    List<RecipeVersionSummary> versions(long establishmentId, long recipeId);

    RecipeVersionView version(long establishmentId, long recipeId, long versionId);

    /** Crea un borrador clonando la última versión; falla si ya hay uno abierto. */
    RecipeVersionView createVersion(long establishmentId, Long actorId, long recipeId);

    /** Cambios de cabecera de un borrador. */
    RecipeVersionView updateVersion(
            long establishmentId, Long actorId, long recipeId, long versionId, RecipeVersionModification modification);

    /** Sustituye el conjunto de ingredientes de un borrador. */
    RecipeVersionView replaceItems(
            long establishmentId, Long actorId, long recipeId, long versionId, List<RecipeItemRegistration> items);

    /** Descarta un borrador. Una versión publicada nunca se borra. */
    void deleteDraft(long establishmentId, Long actorId, long recipeId, long versionId);

    /**
     * Escala una versión a N raciones sin persistir nada (REC-03).
     *
     * <p>Usa siempre el coste vigente: escalar no congela coste, publicar sí.</p>
     */
    ScaledRecipe scale(long establishmentId, long recipeId, long versionId, int rations);
}
