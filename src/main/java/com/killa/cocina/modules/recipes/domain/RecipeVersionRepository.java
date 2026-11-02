package com.killa.cocina.modules.recipes.domain;

import java.util.List;
import java.util.Optional;

/** Puerto de persistencia de las versiones de receta. */
public interface RecipeVersionRepository {

    Optional<RecipeVersion> findById(long establishmentId, long recipeId, long versionId);

    /** Versión por identificador, sin conocer la receta: la usa producción al planificar. */
    Optional<RecipeVersion> findById(long establishmentId, long versionId);

    /** Versiones de una receta, de la más reciente a la más antigua. */
    List<RecipeVersion> findByRecipe(long establishmentId, long recipeId);

    /** Versiones publicadas de todo el establecimiento, por receta y número de versión. */
    List<RecipeVersion> findPublished(long establishmentId);

    /** Última versión por número, sea cual sea su estado. */
    Optional<RecipeVersion> latest(long establishmentId, long recipeId);

    /** Borrador abierto, si lo hay: solo puede haber uno por receta. */
    Optional<RecipeVersion> findDraft(long establishmentId, long recipeId);

    long countByRecipe(long establishmentId, long recipeId);

    RecipeVersion save(RecipeVersion version);

    void delete(long versionId);
}
