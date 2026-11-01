package com.killa.cocina.modules.recipes.domain;

import java.time.Instant;

/**
 * Cambios parciales sobre una receta. Un campo {@code null} significa «no lo toques».
 *
 * <p>La categoría admite tres estados en la API: ausente ({@code null}) la conserva, una cadena
 * vacía la elimina y un texto la sustituye.</p>
 */
public record RecipeModification(String name, String category, Boolean active) {

    public RecipeModification {
        if (name != null) {
            name = RecipeLimits.name(name);
        }
    }

    public boolean isEmpty() {
        return name == null && category == null && active == null;
    }

    public Recipe applyTo(Recipe recipe, Instant now) {
        String normalizedCategory = category == null ? recipe.category() : RecipeLimits.category(category);
        return recipe.update(name, normalizedCategory, active, now);
    }
}
