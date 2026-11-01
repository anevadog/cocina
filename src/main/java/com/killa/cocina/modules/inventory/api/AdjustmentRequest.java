package com.killa.cocina.modules.inventory.api;

import com.killa.cocina.modules.inventory.domain.AdjustmentRegistration;
import com.killa.cocina.modules.inventory.domain.MovementType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

/**
 * Ajuste manual de inventario (INV-05).
 *
 * <p>{@code type} solo admite {@code ADJUSTMENT_IN} o {@code ADJUSTMENT_OUT}; el dominio rechaza
 * cualquier otro valor.</p>
 */
public record AdjustmentRequest(
        @NotNull(message = "es obligatorio") MovementType type,

        @NotNull(message = "es obligatorio")
        @DecimalMin(value = "0", inclusive = false, message = "debe ser mayor que cero")
        BigDecimal quantity,

        @NotBlank(message = "es obligatorio")
        @Size(max = 20, message = "no puede superar 20 caracteres")
        @Pattern(regexp = "[A-Za-z0-9.]{1,20}", message = "solo admite letras, dígitos o punto")
        String unit,

        @NotBlank(message = "es obligatorio")
        @Size(
                min = AdjustmentRegistration.MIN_REASON_LENGTH,
                max = AdjustmentRegistration.MAX_REASON_LENGTH,
                message = "debe tener entre 5 y 200 caracteres")
        String reason) {}
