package com.killa.cocina.modules.quality.domain;

import com.killa.cocina.modules.inventory.domain.Quantities;
import java.math.BigDecimal;
import java.time.Instant;

/**
 * Cabecera de una merma (CAL-03, ADR-039).
 *
 * <p>{@code unitCost} es el PMP aplicado y {@code cost} su producto por la cantidad, congelados en el
 * momento de la merma. El detalle por lote vive en los movimientos {@code WASTE_OUT}.</p>
 */
public record WasteRecord(
        Long id,
        long establishmentId,
        long productId,
        Long lotId,
        BigDecimal quantityBase,
        BigDecimal unitCost,
        BigDecimal cost,
        String reason,
        long actorId,
        Instant occurredAt,
        Instant createdAt) {

    public WasteRecord {
        if (productId <= 0) {
            throw WasteRegistration.invalid("productId", "es obligatorio");
        }
        if (quantityBase == null || quantityBase.signum() <= 0) {
            throw WasteRegistration.invalid("quantityBase", "debe ser mayor que cero");
        }
        if (unitCost == null || unitCost.signum() < 0) {
            throw WasteRegistration.invalid("unitCost", "no puede ser negativo");
        }
        if (cost == null || cost.signum() < 0) {
            throw WasteRegistration.invalid("cost", "no puede ser negativo");
        }
        if (actorId <= 0) {
            throw WasteRegistration.invalid("actorId", "es obligatorio");
        }
        if (occurredAt == null) {
            throw WasteRegistration.invalid("occurredAt", "es obligatorio");
        }
        if (createdAt == null) {
            throw WasteRegistration.invalid("createdAt", "es obligatorio");
        }
        quantityBase = Quantities.stock(quantityBase);
        unitCost = Quantities.cost(unitCost);
        cost = Quantities.cost(cost);
    }

    /**
     * Cabecera provisional sin coste: se guarda antes de bloquear el producto para obtener el id que
     * referencian los movimientos y se completa en la misma transacción (ADR-039).
     */
    public static WasteRecord provisional(
            long establishmentId,
            long productId,
            Long lotId,
            BigDecimal quantityBase,
            String reason,
            long actorId,
            Instant occurredAt,
            Instant createdAt) {
        return new WasteRecord(
                null,
                establishmentId,
                productId,
                lotId,
                quantityBase,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                reason,
                actorId,
                occurredAt,
                createdAt);
    }

    /** Completa la valoración calculada con el PMP vigente. */
    public WasteRecord withCosts(BigDecimal newUnitCost, BigDecimal newCost) {
        return new WasteRecord(
                id,
                establishmentId,
                productId,
                lotId,
                quantityBase,
                newUnitCost,
                newCost,
                reason,
                actorId,
                occurredAt,
                createdAt);
    }
}
