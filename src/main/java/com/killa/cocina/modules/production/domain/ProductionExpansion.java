package com.killa.cocina.modules.production.domain;

import com.killa.cocina.modules.inventory.domain.Quantities;
import com.killa.cocina.modules.recipes.domain.RecipeExpansion;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Agregación de las necesidades de un plan por producto (PRD-02).
 *
 * <p>Suma las cantidades exactas de todos los ítems y redondea <strong>una sola vez</strong> a 3
 * decimales (ADR-036). Redondear cada línea antes de sumar podría dar un total distinto según cómo se
 * reparta la misma receta entre versiones o ítems.</p>
 */
public final class ProductionExpansion {

    private ProductionExpansion() {}

    /** Cantidad necesaria por producto, en unidad base, redondeada una sola vez. */
    public static Map<Long, BigDecimal> consumptionByProduct(List<PlanLine> lines) {
        Map<Long, BigDecimal> exact = new LinkedHashMap<>();
        for (PlanLine line : lines) {
            int rations = ProductionLimits.rations(line.rations(), "rations");
            for (ProductionCompositionItem item : line.items()) {
                BigDecimal consumption =
                        RecipeExpansion.consumptionExact(item.quantityBase(), item.wastePct(), rations);
                exact.merge(item.productId(), consumption, BigDecimal::add);
            }
        }
        Map<Long, BigDecimal> rounded = new LinkedHashMap<>();
        exact.forEach((productId, quantity) -> rounded.put(productId, Quantities.stock(quantity)));
        return rounded;
    }

    /** Composición de un ítem y las raciones para las que se expande. */
    public record PlanLine(List<ProductionCompositionItem> items, int rations) {

        public PlanLine {
            items = items == null ? List.of() : List.copyOf(items);
        }
    }
}
