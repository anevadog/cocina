package com.killa.cocina.modules.inventory.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * Puerto del kardex.
 *
 * <p>No expone actualización ni borrado: un movimiento no se corrige, se compensa con otro.</p>
 */
public interface StockMovementRepository {

    StockMovement save(StockMovement movement);

    List<StockMovement> findByProduct(long establishmentId, long productId, int page, int size);

    long countByProduct(long establishmentId, long productId);

    /** Consumo {@code PRODUCTION_OUT} por producto para los batches indicados (ECO-03). */
    Map<Long, BigDecimal> productionOutByBatches(long establishmentId, Collection<Long> batchIds);

    /** Ajustes netos de salida por producto en el intervalo {@code [from, to)} (ECO-03). */
    Map<Long, BigDecimal> netAdjustmentOutflow(long establishmentId, Instant fromInclusive, Instant toExclusive);
}
