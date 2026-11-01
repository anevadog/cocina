package com.killa.cocina.modules.recipes.domain;

import java.math.BigDecimal;
import java.util.Locale;

/**
 * Ingrediente tal y como llega en la petición (REC-02).
 *
 * <p>La cantidad se expresa en cualquier unidad admitida por el producto y se convierte a la unidad
 * base en el servicio de aplicación, que es quien conoce el catálogo. El orden de la lista fija
 * {@code sortOrder}.</p>
 */
public record RecipeItemRegistration(long productId, BigDecimal quantity, String unit, BigDecimal wastePct) {

    public RecipeItemRegistration {
        if (productId <= 0) {
            throw RecipeLimits.invalid("productId", "es obligatorio");
        }
        if (quantity == null || quantity.signum() <= 0) {
            throw RecipeLimits.invalid("quantity", "debe ser mayor que cero");
        }
        if (unit == null || unit.isBlank()) {
            throw RecipeLimits.invalid("unit", "es obligatoria");
        }
        unit = unit.trim().toLowerCase(Locale.ROOT);
        wastePct = RecipeLimits.waste(wastePct);
    }
}
