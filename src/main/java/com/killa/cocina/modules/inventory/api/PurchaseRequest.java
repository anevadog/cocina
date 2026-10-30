package com.killa.cocina.modules.inventory.api;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;

/**
 * Entrada de compra con lote (INV-04).
 *
 * <p>{@code unitPrice} es el precio por unidad de compra, no por unidad base; el servicio lo
 * convierte con el factor del producto.</p>
 */
public record PurchaseRequest(
        @NotNull(message = "es obligatorio")
        @Positive(message = "debe ser mayor que cero")
        Long supplierId,

        @NotNull(message = "es obligatorio")
        @DecimalMin(value = "0", inclusive = false, message = "debe ser mayor que cero")
        BigDecimal quantity,

        @NotBlank(message = "es obligatorio")
        @Size(max = 20, message = "no puede superar 20 caracteres")
        @Pattern(regexp = "[A-Za-z0-9.]{1,20}", message = "solo admite letras, dígitos o punto")
        String unit,

        @NotNull(message = "es obligatorio")
        @DecimalMin(value = "0", message = "no puede ser negativo")
        BigDecimal unitPrice,

        Instant receivedAt,

        Instant expiresAt) {
}
