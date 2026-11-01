package com.killa.cocina.modules.recipes.api;

import jakarta.validation.constraints.Size;

/**
 * Edición parcial de la cabecera de una receta.
 *
 * <p>Un campo ausente no se toca. Una categoría vacía la elimina.</p>
 */
public record UpdateRecipeRequest(
        @Size(min = 2, max = 120, message = "debe tener entre 2 y 120 caracteres")
        String name,

        @Size(max = 80, message = "no puede superar 80 caracteres")
        String category,

        Boolean active) {}
