package com.killa.cocina.modules.inventory.domain;

import java.util.List;

/**
 * Puerto del kardex.
 *
 * <p>No expone actualización ni borrado: un movimiento no se corrige, se compensa con otro.</p>
 */
public interface StockMovementRepository {

    StockMovement save(StockMovement movement);

    List<StockMovement> findByProduct(long establishmentId, long productId, int page, int size);

    long countByProduct(long establishmentId, long productId);
}
