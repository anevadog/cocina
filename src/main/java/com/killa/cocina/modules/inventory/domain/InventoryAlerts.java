package com.killa.cocina.modules.inventory.domain;

import java.util.List;

/**
 * Alertas operativas del inventario (INV-07, ECO-06).
 *
 * <p>Las dos listas se limitan para que la respuesta sea acotada; {@code *Total} indica cuántos
 * elementos hay de verdad.</p>
 */
public record InventoryAlerts(
        List<LowStockAlert> lowStock, long lowStockTotal, List<ExpiringLotAlert> expiring, long expiringTotal) {

    public InventoryAlerts {
        lowStock = lowStock == null ? List.of() : List.copyOf(lowStock);
        expiring = expiring == null ? List.of() : List.copyOf(expiring);
    }
}
