package com.killa.cocina.modules.inventory.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/** Puerto de persistencia de lotes de stock. */
public interface StockLotRepository {

    StockLot save(StockLot lot);

    Optional<StockLot> findById(long establishmentId, long lotId);

    /** Lotes con saldo, en orden FIFO por caducidad (sin fecha al final) y recepción. */
    List<StockLot> findConsumable(long establishmentId, long productId);

    /**
     * Lotes con saldo no caducados en {@code now}, en el mismo orden FIFO que {@link #findConsumable}.
     *
     * <p>Es el único punto de consumo que excluye los caducados: un ajuste manual sí puede
     * retirarlos, pero producción nunca debe llevar un lote caducado al plato (ADR-030, CAL-04).</p>
     */
    List<StockLot> findUsable(long establishmentId, long productId, Instant now);

    List<StockLot> findByProduct(long establishmentId, long productId, int page, int size);

    long countByProduct(long establishmentId, long productId);

    /** Lotes con saldo que caducan antes de {@code until}, con el nombre del producto. */
    List<ExpiringLotRow> findExpiring(long establishmentId, Instant until, int limit);

    long countExpiring(long establishmentId, Instant until);

    /** Valor total al coste del lote de todo lo que caduca antes de {@code until} y aún tiene saldo. */
    BigDecimal valueExpiring(long establishmentId, Instant until);
}
