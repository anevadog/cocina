package com.killa.cocina.modules.recipes.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.List;

/**
 * Sustitución del conjunto completo de ingredientes de un borrador (REC-02).
 *
 * <p>El orden de la lista es el orden de la receta. Una lista vacía deja el borrador sin
 * ingredientes; publicarlo seguirá estando prohibido.</p>
 */
public record ReplaceRecipeItemsRequest(
        @NotNull(message = "es obligatorio") List<@Valid RecipeItemRequest> items) {}
