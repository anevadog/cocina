package com.killa.cocina.modules.recipes.domain;

import java.math.BigDecimal;
import java.util.List;

/**
 * Receta escalada a N raciones, sin persistir nada (REC-03, REC-04).
 *
 * <p>Se calcula siempre con el PMP vigente: escalar no congela coste, publicar sí. El coste del lote
 * se deriva del coste por ración, así que ambas cifras son coherentes entre sí.</p>
 *
 * @param costPerRation coste por ración; {@code null} si falta el coste de algún ingrediente
 * @param totalCost coste del lote de {@code rations} raciones; {@code null} en el mismo caso
 * @param ingredients ingredientes expandidos, en el orden de la receta
 */
public record ScaledRecipe(
        long recipeId,
        long versionId,
        int versionNumber,
        int rations,
        BigDecimal costPerRation,
        BigDecimal totalCost,
        List<Long> missingProducts,
        List<ScaledIngredient> ingredients) {

    public ScaledRecipe {
        rations = RecipeExpansion.rations(rations);
        missingProducts = missingProducts == null ? List.of() : List.copyOf(missingProducts);
        ingredients = ingredients == null ? List.of() : List.copyOf(ingredients);
    }

    /** El coste es utilizable solo si todos los ingredientes tienen coste conocido. */
    public boolean costComplete() {
        return missingProducts.isEmpty();
    }
}
