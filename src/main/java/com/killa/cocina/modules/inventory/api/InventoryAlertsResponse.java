package com.killa.cocina.modules.inventory.api;

import com.killa.cocina.modules.inventory.domain.InventoryAlerts;
import java.util.List;

/**
 * Alertas operativas del inventario (INV-07, ECO-06).
 *
 * <p>Cada lista se limita a 100 elementos; los totales indican cuántos hay en realidad.</p>
 */
public record InventoryAlertsResponse(List<LowStockAlertResponse> lowStock, long lowStockTotal,
        List<ExpiringLotAlertResponse> expiring, long expiringTotal) {

    public static InventoryAlertsResponse from(InventoryAlerts alerts) {
        return new InventoryAlertsResponse(
                alerts.lowStock().stream().map(LowStockAlertResponse::from).toList(),
                alerts.lowStockTotal(),
                alerts.expiring().stream().map(ExpiringLotAlertResponse::from).toList(),
                alerts.expiringTotal());
    }
}
