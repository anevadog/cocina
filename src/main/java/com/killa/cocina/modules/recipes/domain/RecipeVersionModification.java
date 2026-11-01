package com.killa.cocina.modules.recipes.domain;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Cambios parciales sobre la cabecera de un borrador (REC-01).
 *
 * <p>{@code null} deja el campo como está. En los textos, una cadena vacía los elimina. El tiempo de
 * preparación solo se cambia por otro valor: no se borra en el MVP.</p>
 */
public record RecipeVersionModification(
        Integer baseRations, BigDecimal yieldPct, Integer prepTimeMinutes, String instructions) {

    public boolean isEmpty() {
        return baseRations == null && yieldPct == null && prepTimeMinutes == null && instructions == null;
    }

    public RecipeVersion applyTo(RecipeVersion version, Instant now) {
        int newRations = baseRations == null ? version.baseRations() : RecipeLimits.baseRations(baseRations);
        BigDecimal newYield = yieldPct == null ? version.yieldPct() : RecipeLimits.yieldPct(yieldPct);
        Integer newPrepTime =
                prepTimeMinutes == null ? version.prepTimeMinutes() : RecipeLimits.prepTimeMinutes(prepTimeMinutes);
        String newInstructions =
                instructions == null ? version.instructions() : RecipeLimits.instructions(instructions);
        return version.withHeader(newRations, newYield, newPrepTime, newInstructions, now);
    }
}
