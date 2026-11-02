package com.killa.cocina.modules.production.api;

import com.killa.cocina.modules.production.domain.ProductionPlanRegistration;
import com.killa.cocina.modules.production.domain.ServiceType;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

/**
 * Alta del plan de un servicio (PRD-01).
 *
 * <p>Solo puede existir un plan por fecha y servicio; el duplicado responde 409
 * {@code RESOURCE_IN_USE}.</p>
 */
public record CreateProductionPlanRequest(
        @NotNull(message = "es obligatoria") LocalDate serviceDate,

        @NotNull(message = "es obligatorio") ServiceType serviceType) {

    public ProductionPlanRegistration toDomain() {
        return new ProductionPlanRegistration(serviceDate, serviceType);
    }
}
