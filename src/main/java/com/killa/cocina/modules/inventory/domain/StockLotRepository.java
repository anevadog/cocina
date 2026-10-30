package com.killa.cocina.modules.inventory.domain;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/** Puerto de persistencia de lotes de stock. */
public interface StockLotRepository {

    StockLot save(StockLot lot);

    Optional<StockLot> findById(long establishmentId, long lotId);

    /** Lotes con saldo, en orden FIFO por caducidad (sin fecha al final) y recepción. */
    List<StockLot> findConsumable(long establishmentId, long productId);

    List<StockLot> findByProduct(long establishmentId, long productId, int page, int size);

    long countByProduct(long establishmentId, long productId);

    /** Lotes con saldo que caducan antes de {@code until}, con el nombre del producto. */
    List<ExpiringLotRow> findExpiring(long establishmentId, Instant until, int limit);

    long countExpiring(long establishmentId, Instant until);
}
