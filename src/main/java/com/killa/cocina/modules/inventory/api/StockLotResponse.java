package com.killa.cocina.modules.inventory.api;

import com.killa.cocina.modules.inventory.domain.StockLot;
import java.math.BigDecimal;
import java.time.Instant;

/** Lote de stock expuesto por la API (INV-04, CAL-01). */
public record StockLotResponse(
        Long id,
        Long supplierId,
        Instant receivedAt,
        Instant expiresAt,
        BigDecimal unitCost,
        BigDecimal initialQty,
        BigDecimal remainingQty,
        BigDecimal value) {

    public static StockLotResponse from(StockLot lot) {
        return new StockLotResponse(
                lot.id(),
                lot.supplierId(),
                lot.receivedAt(),
                lot.expiresAt(),
                lot.unitCost(),
                lot.initialQty(),
                lot.remainingQty(),
                lot.value());
    }
}
