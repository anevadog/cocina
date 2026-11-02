package com.killa.cocina.modules.production.domain;

import java.util.List;
import java.util.Optional;

/** Puerto de persistencia de los ítems de un plan. */
public interface ProductionPlanItemRepository {

    List<ProductionPlanItem> findByPlan(long planId);

    Optional<ProductionPlanItem> findById(long establishmentId, long planId, long itemId);

    /** Sustituye el conjunto completo de ítems; solo se usa con el plan en {@code PENDING}. */
    void replaceForPlan(long planId, List<ProductionPlanItem> items);

    void delete(long itemId);
}
