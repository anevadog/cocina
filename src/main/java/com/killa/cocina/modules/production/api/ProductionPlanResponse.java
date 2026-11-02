package com.killa.cocina.modules.production.api;

import com.killa.cocina.modules.production.domain.ProductionPlanView;
import com.killa.cocina.modules.production.domain.ProductionStatus;
import com.killa.cocina.modules.production.domain.ServiceType;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/** Plan de producción expuesto por la API (PRD-01..05). */
public record ProductionPlanResponse(
        Long id,
        LocalDate serviceDate,
        ServiceType serviceType,
        ProductionStatus status,
        Instant createdAt,
        Instant updatedAt,
        List<ProductionItemResponse> items) {

    public static ProductionPlanResponse from(ProductionPlanView view) {
        return new ProductionPlanResponse(
                view.id(),
                view.serviceDate(),
                view.serviceType(),
                view.status(),
                view.createdAt(),
                view.updatedAt(),
                view.items().stream().map(ProductionItemResponse::from).toList());
    }
}
