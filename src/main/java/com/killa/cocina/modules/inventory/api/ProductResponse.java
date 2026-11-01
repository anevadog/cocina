package com.killa.cocina.modules.inventory.api;

import com.killa.cocina.modules.inventory.domain.BaseUnit;
import com.killa.cocina.modules.inventory.domain.ProductView;
import java.math.BigDecimal;
import java.util.List;

/**
 * Producto expuesto por la API (INV-01, INV-07).
 *
 * @param lowStock calculado: activo y por debajo del stock mínimo
 */
public record ProductResponse(
        Long id,
        long categoryId,
        String name,
        BaseUnit baseUnit,
        BigDecimal minStock,
        BigDecimal currentStock,
        BigDecimal currentAvgCost,
        boolean allowNegative,
        boolean active,
        boolean lowStock,
        List<ConversionResponse> conversions) {

    public static ProductResponse from(ProductView view) {
        return new ProductResponse(
                view.id(),
                view.categoryId(),
                view.name(),
                view.baseUnit(),
                view.minStock(),
                view.currentStock(),
                view.currentAvgCost(),
                view.allowNegative(),
                view.active(),
                view.lowStock(),
                view.conversions().stream().map(ConversionResponse::from).toList());
    }
}
