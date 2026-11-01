package com.killa.cocina.modules.inventory.domain;

import java.math.BigDecimal;
import java.util.List;

/**
 * Vista de un producto para la API, con sus conversiones y su estado de alerta (INV-07).
 *
 * <p>El stock y el coste son los vigentes; el histórico se consulta en el kardex.</p>
 */
public record ProductView(
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
        List<UnitConversion> conversions) {

    public static ProductView from(Product product, List<UnitConversion> conversions) {
        return new ProductView(
                product.id(),
                product.categoryId(),
                product.name(),
                product.baseUnit(),
                product.minStock(),
                product.currentStock(),
                product.currentAvgCost(),
                product.allowNegative(),
                product.active(),
                product.isLowStock(),
                conversions);
    }
}
