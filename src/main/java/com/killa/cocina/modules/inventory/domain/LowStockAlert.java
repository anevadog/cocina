package com.killa.cocina.modules.inventory.domain;

import java.math.BigDecimal;

/** Producto activo por debajo de su stock mínimo (INV-07). */
public record LowStockAlert(Long productId, String name, BigDecimal currentStock,
        BigDecimal minStock) {
}
