package com.killa.cocina.modules.production.api;

import com.killa.cocina.modules.production.domain.ProductionStatus;
import jakarta.validation.constraints.NotNull;

/**
 * Edición de la cabecera del plan (PRD-04).
 *
 * <p>En F5 solo se admite {@code CANCELLED}: el resto de estados avanza con la ejecución y no se
 * fija a mano. Cancelar solo es posible mientras el plan está pendiente y no descuenta stock.</p>
 */
public record UpdateProductionPlanRequest(
        @NotNull(message = "es obligatorio") ProductionStatus status) {}
