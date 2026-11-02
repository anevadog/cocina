package com.killa.cocina.modules.production.domain;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/** Puerto de persistencia de los planes de producción. */
public interface ProductionPlanRepository {

    Optional<ProductionPlan> findById(long establishmentId, long planId);

    /** Plan de una fecha y servicio, para rechazar duplicados (PRD-01). */
    Optional<ProductionPlan> findByService(long establishmentId, LocalDate serviceDate, ServiceType serviceType);

    List<ProductionPlan> findByDate(long establishmentId, LocalDate serviceDate, int page, int size);

    long countByDate(long establishmentId, LocalDate serviceDate);

    ProductionPlan save(ProductionPlan plan);

    void delete(long planId);
}
