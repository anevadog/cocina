package com.killa.cocina.modules.recipes.domain;

import com.killa.cocina.modules.inventory.domain.Quantities;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Aritmética única de composición, consumo y coste de una receta (REC-02, REC-03, REC-04).
 *
 * <p>Todo el módulo calcula aquí: el escalado, el coste actual de una versión y, en F5, el descuento
 * de stock por producción. Se reutilizan las escalas de {@link Quantities} para que una receta y una
 * compra no redondeen distinto (ADR-021).</p>
 *
 * <p><strong>Un solo redondeo por magnitud</strong>: la cantidad se calcula de forma exacta y se
 * redondea una vez a 3 decimales; el coste se suma exacto y se redondea una vez a 4 (ADR-033).</p>
 */
public final class RecipeExpansion {

    private RecipeExpansion() {}

    /** Valida el número de raciones de un escalado y lo devuelve (REC-03). */
    public static int rations(int rations) {
        if (rations < 1 || rations > RecipeLimits.MAX_RATIONS) {
            throw RecipeLimits.invalid("rations", "debe estar entre 1 y " + RecipeLimits.MAX_RATIONS);
        }
        return rations;
    }

    /**
     * Cantidad consumida de un ingrediente para {@code rations} raciones, con su merma.
     *
     * <p>{@code quantityBase} es por ración y en unidad base; la merma se suma a la cantidad neta
     * (ADR-033). 250 g al 0 % para 3 raciones son 750,000 g.</p>
     */
    public static BigDecimal consumption(BigDecimal quantityBase, BigDecimal wastePct, int rations) {
        if (rations < 1) {
            throw new IllegalArgumentException("las raciones deben ser al menos 1");
        }
        BigDecimal waste = wastePct == null ? BigDecimal.ZERO : wastePct;
        return quantityBase
                .multiply(BigDecimal.ONE.add(waste.movePointLeft(2)))
                .multiply(BigDecimal.valueOf(rations))
                .setScale(Quantities.STOCK_SCALE, Quantities.ROUNDING);
    }

    /** Coste de una ración con los costes unitarios indicados, redondeado a 4 decimales. */
    public static RecipeCost costPerRation(List<RecipeItem> items, Map<Long, BigDecimal> unitCosts) {
        BigDecimal total = BigDecimal.ZERO;
        List<Long> missing = new ArrayList<>();
        for (RecipeItem item : items) {
            BigDecimal unitCost = unitCosts.get(item.productId());
            if (unitCost == null) {
                missing.add(item.productId());
                continue;
            }
            total = total.add(
                    consumption(item.quantityBase(), item.wastePct(), 1).multiply(unitCost));
        }
        BigDecimal perRation = missing.isEmpty() ? total.setScale(Quantities.COST_SCALE, Quantities.ROUNDING) : null;
        return new RecipeCost(perRation, missing);
    }

    /** Coste informativo de una línea: cantidad consumida por coste unitario, redondeado a 4. */
    public static BigDecimal lineCost(BigDecimal quantity, BigDecimal unitCost) {
        if (quantity == null || unitCost == null) {
            return null;
        }
        return quantity.multiply(unitCost).setScale(Quantities.COST_SCALE, Quantities.ROUNDING);
    }

    /** Coste del lote de {@code rations} raciones a partir del coste por ración ya redondeado. */
    public static BigDecimal batchCost(BigDecimal costPerRation, int rations) {
        if (costPerRation == null) {
            return null;
        }
        return costPerRation.multiply(BigDecimal.valueOf(rations)).setScale(Quantities.COST_SCALE, Quantities.ROUNDING);
    }
}
