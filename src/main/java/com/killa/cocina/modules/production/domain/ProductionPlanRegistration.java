package com.killa.cocina.modules.production.domain;

import java.time.LocalDate;

/** Datos para crear el plan de un servicio (PRD-01). */
public record ProductionPlanRegistration(LocalDate serviceDate, ServiceType serviceType) {

    public ProductionPlanRegistration {
        if (serviceDate == null) {
            throw ProductionLimits.invalid("serviceDate", "es obligatoria");
        }
        if (serviceType == null) {
            throw ProductionLimits.invalid("serviceType", "es obligatorio");
        }
    }
}
