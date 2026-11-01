package com.killa.cocina.modules.recipes.api;

import com.killa.cocina.modules.recipes.domain.RecipeItemRegistration;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

/**
 * Ingrediente de una receta (REC-02).
 *
 * <p>La cantidad es por ración y en cualquier unidad admitida por el producto; el servicio la
 * convierte a su unidad base.</p>
 */
public record RecipeItemRequest(
        @NotNull(message = "es obligatorio") @Positive(message = "debe ser mayor que cero")
        Long productId,

        @NotNull(message = "es obligatorio")
        @DecimalMin(value = "0", inclusive = false, message = "debe ser mayor que cero")
        BigDecimal quantity,

        @NotBlank(message = "es obligatoria") @Size(max = 20, message = "no puede superar 20 caracteres")
        String unit,

        @DecimalMin(value = "0", message = "no puede ser negativa")
        @DecimalMax(value = "99.99", message = "no puede superar 99.99")
        BigDecimal wastePct) {

    public RecipeItemRegistration toDomain() {
        return new RecipeItemRegistration(productId == null ? 0 : productId, quantity, unit, wastePct);
    }
}
