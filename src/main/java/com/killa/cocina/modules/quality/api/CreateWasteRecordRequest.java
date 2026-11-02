package com.killa.cocina.modules.quality.api;

import com.killa.cocina.modules.quality.domain.WasteRegistration;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

/**
 * Alta de una merma (CAL-03).
 *
 * <p>{@code quantityBase} va en la unidad base del producto y {@code lotId} es opcional: sin lote se
 * consume en orden FIFO.</p>
 */
public record CreateWasteRecordRequest(
        @NotNull(message = "es obligatorio") @Positive(message = "debe ser mayor que cero")
        Long productId,

        @Positive(message = "debe ser mayor que cero") Long lotId,

        @NotNull(message = "es obligatoria")
        @DecimalMin(value = "0", inclusive = false, message = "debe ser mayor que cero")
        BigDecimal quantityBase,

        @NotBlank(message = "es obligatorio") @Size(min = 5, max = 200, message = "debe tener entre 5 y 200 caracteres")
        String reason) {

    public WasteRegistration toRegistration() {
        return new WasteRegistration(productId, lotId, quantityBase, reason);
    }
}
