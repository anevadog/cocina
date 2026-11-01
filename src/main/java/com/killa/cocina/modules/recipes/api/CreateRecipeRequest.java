package com.killa.cocina.modules.recipes.api;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

/**
 * Alta de receta con su primera versión en borrador (REC-01).
 *
 * <p>Los ingredientes se añaden después; una receta sin ingredientes puede existir como borrador,
 * pero no se publica.</p>
 */
public record CreateRecipeRequest(
        @NotBlank(message = "es obligatorio") @Size(min = 2, max = 120, message = "debe tener entre 2 y 120 caracteres")
        String name,

        @Size(max = 80, message = "no puede superar 80 caracteres")
        String category,

        @NotNull(message = "es obligatorio")
        @Min(value = 1, message = "debe ser al menos 1")
        @Max(value = 10000, message = "no puede superar 10000")
        Integer baseRations,

        @DecimalMin(value = "0", inclusive = false, message = "debe ser mayor que 0")
        @DecimalMax(value = "100", message = "no puede superar 100")
        BigDecimal yieldPct,

        @Min(value = 1, message = "debe ser al menos 1") @Max(value = 1440, message = "no puede superar 1440")
        Integer prepTimeMinutes,

        @Size(max = 4000, message = "no puede superar 4000 caracteres")
        String instructions) {}
