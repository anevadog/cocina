package com.killa.cocina.modules.inventory.api;

import com.killa.cocina.modules.inventory.domain.LowStockAlert;
import java.math.BigDecimal;

/** Producto por debajo de su stock mínimo (INV-07). */
public record LowStockAlertResponse(Long productId, String name, BigDecimal currentStock,
        BigDecimal minStock) {

    public static LowStockAlertResponse from(LowStockAlert alert) {
        return new LowStockAlertResponse(alert.productId(), alert.name(), alert.currentStock(),
                alert.minStock());
    }
}
