package com.killa.cocina.modules.recipes.api;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

/**
 * Edición parcial de la cabecera de un borrador (REC-01).
 *
 * <p>Un campo ausente no se toca; una cadena vacía borra el texto correspondiente.</p>
 */
public record UpdateRecipeVersionRequest(
        @Min(value = 1, message = "debe ser al menos 1") @Max(value = 10000, message = "no puede superar 10000")
        Integer baseRations,

        @DecimalMin(value = "0", inclusive = false, message = "debe ser mayor que 0")
        @DecimalMax(value = "100", message = "no puede superar 100")
        BigDecimal yieldPct,

        @Min(value = 1, message = "debe ser al menos 1") @Max(value = 1440, message = "no puede superar 1440")
        Integer prepTimeMinutes,

        @Size(max = 4000, message = "no puede superar 4000 caracteres")
        String instructions) {}
