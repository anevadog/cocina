package com.killa.cocina.modules.inventory.domain;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;

/**
 * Lote de stock con su saldo físico y su coste (INV-04, CAL-01, ADR-025).
 *
 * <p>{@code remainingQty} es lo que queda del lote; la suma de los lotes de un producto coincide con
 * {@code products.current_stock}. El lote nace en una compra o en un ajuste positivo (sin proveedor
 * ni caducidad).</p>
 */
public record StockLot(Long id, long establishmentId, long productId, Long supplierId,
        Instant receivedAt, Instant expiresAt, BigDecimal unitCost, BigDecimal initialQty,
        BigDecimal remainingQty, Instant createdAt) {

    public StockLot {
        if (unitCost == null || unitCost.signum() < 0) {
            throw Quantities.invalid("unitCost", "no puede ser negativo");
        }
        if (initialQty == null || initialQty.signum() <= 0) {
            throw Quantities.invalid("initialQty", "debe ser mayor que cero");
        }
        if (remainingQty == null || remainingQty.signum() < 0
                || remainingQty.compareTo(initialQty) > 0) {
            throw Quantities.invalid("remainingQty", "debe estar entre cero y la cantidad inicial");
        }
        unitCost = Quantities.cost(unitCost);
        initialQty = Quantities.stock(initialQty);
        remainingQty = Quantities.stock(remainingQty);
    }

    /** Alta de lote; el saldo inicial y el restante coinciden. */
    public static StockLot create(long establishmentId, long productId, Long supplierId,
            Instant receivedAt, Instant expiresAt, BigDecimal unitCost, BigDecimal quantity,
            Instant now) {
        return new StockLot(null, establishmentId, productId, supplierId, receivedAt, expiresAt,
                unitCost, quantity, quantity, now);
    }

    /** Un lote con fecha es caducado desde ese instante (CAL-04). */
    public boolean isExpired(Instant now) {
        return expiresAt != null && !expiresAt.isAfter(now);
    }

    /** Entra en la alerta si caduca dentro de la ventana y aún queda stock. */
    public boolean expiresWithin(Instant now, Duration window) {
        return expiresAt != null && remainingQty.signum() > 0
                && !expiresAt.isAfter(now.plus(window));
    }

    /** Valor del saldo restante, redondeado a la escala monetaria. */
    public BigDecimal value() {
        return Quantities.cost(remainingQty.multiply(unitCost));
    }

    /** Descuenta cantidad del lote; la cantidad no puede superar el saldo restante. */
    public StockLot consume(BigDecimal quantity) {
        if (quantity == null || quantity.signum() <= 0
                || quantity.compareTo(remainingQty) > 0) {
            throw new IllegalArgumentException("Consumo de lote fuera de rango: " + quantity);
        }
        return new StockLot(id, establishmentId, productId, supplierId, receivedAt, expiresAt,
                unitCost, initialQty, remainingQty.subtract(quantity), createdAt);
    }
}
