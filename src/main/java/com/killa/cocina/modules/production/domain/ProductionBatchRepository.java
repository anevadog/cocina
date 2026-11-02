package com.killa.cocina.modules.production.domain;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/** Puerto de persistencia de los batches de producción. */
public interface ProductionBatchRepository {

    Optional<ProductionBatch> findById(long establishmentId, long batchId);

    /** Bloquea el batch para que una transición simultánea no descuente dos veces. */
    Optional<ProductionBatch> findByIdForUpdate(long establishmentId, long batchId);

    List<ProductionBatch> findByPlanItems(Collection<Long> planItemIds);

    /** Batches terminados en el intervalo {@code [from, to)}, para los informes (PRD-05, ECO-03). */
    List<ProductionBatch> findFinishedBetween(long establishmentId, Instant fromInclusive, Instant toExclusive);

    Optional<ProductionBatch> findByPlanItem(long planItemId);

    boolean existsByPlanItem(long planItemId);

    ProductionBatch save(ProductionBatch batch);
}
