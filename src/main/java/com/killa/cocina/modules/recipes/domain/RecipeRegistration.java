package com.killa.cocina.modules.recipes.domain;

import java.math.BigDecimal;

/**
 * Datos para crear una receta con su primera versión en borrador (REC-01).
 *
 * <p>Valida en el constructor: si el objeto existe, es coherente. Los ingredientes se añaden
 * después con {@link RecipeItemRegistration}.</p>
 */
public record RecipeRegistration(
        String name,
        String category,
        int baseRations,
        BigDecimal yieldPct,
        Integer prepTimeMinutes,
        String instructions) {

    public RecipeRegistration {
        name = RecipeLimits.name(name);
        category = RecipeLimits.category(category);
        baseRations = RecipeLimits.baseRations(baseRations);
        yieldPct = RecipeLimits.yieldPct(yieldPct);
        prepTimeMinutes = RecipeLimits.prepTimeMinutes(prepTimeMinutes);
        instructions = RecipeLimits.instructions(instructions);
    }
}
