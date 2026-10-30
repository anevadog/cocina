package com.killa.cocina.modules.inventory.domain;

import java.math.BigDecimal;

/**
 * Valoración del stock con coste medio ponderado (INV-09, ADR-021, ADR-028).
 *
 * <p>La fórmula usa el saldo y el PMP vigentes: si el saldo no es positivo, un saldo negativo no
 * tiene valoración fiable y la entrada pasa a fijar el PMP. Así nunca se divide por cero ni se
 * arrastra un coste sin sentido.</p>
 */
public final class Valuation {

    private Valuation() {
    }

    /**
     * Nuevo PMP tras una entrada de {@code quantityBase} valorada a {@code unitCostBase}.
     *
     * @return coste medio ponderado a cuatro decimales
     */
    public static BigDecimal averageCost(Product product, BigDecimal quantityBase,
            BigDecimal unitCostBase) {
        if (quantityBase == null || quantityBase.signum() <= 0) {
            throw Quantities.invalid("quantity", "la cantidad debe ser mayor que cero");
        }
        BigDecimal currentStock = product.currentStock();
        if (currentStock == null || currentStock.signum() <= 0) {
            // D13: sin saldo positivo no hay valor que ponderar; la entrada fija el coste.
            return Quantities.cost(unitCostBase);
        }
        BigDecimal currentValue = currentStock.multiply(product.currentAvgCost());
        BigDecimal incomingValue = quantityBase.multiply(unitCostBase);
        BigDecimal newStock = currentStock.add(quantityBase);
        return currentValue.add(incomingValue)
                .divide(newStock, Quantities.COST_SCALE, Quantities.ROUNDING);
    }
}
