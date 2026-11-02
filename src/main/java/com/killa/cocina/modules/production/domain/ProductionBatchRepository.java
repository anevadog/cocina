package com.killa.cocina.modules.production.domain;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/** Puerto de persistencia de los batches de producción. */
public interface ProductionBatchRepository {

    Optional<ProductionBatch> findById(long establishmentId, long batchId);

    List<ProductionBatch> findByPlanItems(Collection<Long> planItemIds);

    Optional<ProductionBatch> findByPlanItem(long planItemId);

    boolean existsByPlanItem(long planItemId);

    ProductionBatch save(ProductionBatch batch);
}
