package com.killa.cocina.modules.production.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/** Plan completo para la API, con sus ítems y el estado de cada ejecución (PRD-01..05). */
public record ProductionPlanView(
        Long id,
        LocalDate serviceDate,
        ServiceType serviceType,
        ProductionStatus status,
        Instant createdAt,
        Instant updatedAt,
        List<ProductionItemView> items) {

    public ProductionPlanView {
        items = items == null ? List.of() : List.copyOf(items);
    }
}
