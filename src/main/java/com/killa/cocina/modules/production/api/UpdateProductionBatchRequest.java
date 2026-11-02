package com.killa.cocina.modules.production.api;

import com.killa.cocina.modules.production.domain.ProductionStatus;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Transición de un batch de producción (PRD-03..05).
 *
 * <p>{@code rations} es obligatorio al pasar a {@code READY}; si difiere de lo previsto, el dominio
 * exige {@code deviationReason}. {@code SERVED} y {@code CANCELLED} no descuentan stock.</p>
 */
public record UpdateProductionBatchRequest(
        @NotNull(message = "es obligatorio") ProductionStatus status,

        @Min(value = 1, message = "debe ser al menos 1") @Max(value = 10000, message = "no puede superar 10000")
        Integer rations,

        @Size(max = 200, message = "no puede superar 200 caracteres")
        String deviationReason) {}
