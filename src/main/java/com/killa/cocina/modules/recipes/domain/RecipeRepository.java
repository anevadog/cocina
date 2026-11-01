package com.killa.cocina.modules.recipes.domain;

import java.util.List;
import java.util.Optional;

/** Puerto de persistencia de recetas. */
public interface RecipeRepository {

    Optional<Recipe> findById(long establishmentId, long recipeId);

    boolean existsByName(long establishmentId, String name);

    /** Comprueba el nombre excluyendo una receta, para renombrar sin chocar consigo misma. */
    boolean existsByNameExcluding(long establishmentId, String name, long excludedRecipeId);

    Recipe save(Recipe recipe);

    List<Recipe> search(long establishmentId, RecipeFilter filter, int page, int size);

    long count(long establishmentId, RecipeFilter filter);
}
