package com.killa.cocina.modules.inventory.domain;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Fila del kardex (INV-06, INV-09).
 *
 * <p>Inmutable por diseño: no tiene setters ni operaciones de cambio. {@code balanceAfter} es el
 * saldo resultante y {@code unitCost} el coste aplicado por unidad base, congelado en el momento del
 * hecho.</p>
 */
public record StockMovement(
        Long id,
        long establishmentId,
        long productId,
        Long lotId,
        MovementType type,
        BigDecimal quantityBase,
        BigDecimal unitCost,
        BigDecimal balanceAfter,
        String reason,
        String referenceType,
        Long referenceId,
        long actorId,
        Instant occurredAt) {

    public StockMovement {
        if (type == null) {
            throw Quantities.invalid("type", "el tipo de movimiento es obligatorio");
        }
        if (quantityBase == null || quantityBase.signum() <= 0) {
            throw Quantities.invalid("quantityBase", "debe ser mayor que cero");
        }
        if (unitCost == null || unitCost.signum() < 0) {
            throw Quantities.invalid("unitCost", "no puede ser negativo");
        }
        if (balanceAfter == null) {
            throw Quantities.invalid("balanceAfter", "el saldo resultante es obligatorio");
        }
        quantityBase = Quantities.stock(quantityBase);
        unitCost = Quantities.cost(unitCost);
        balanceAfter = Quantities.stock(balanceAfter);
    }
}
