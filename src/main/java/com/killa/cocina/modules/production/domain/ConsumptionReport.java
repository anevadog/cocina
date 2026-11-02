package com.killa.cocina.modules.production.domain;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Consumo teórico frente a real por producto (ECO-03).
 *
 * <p>El teórico es la receta sin merma para las raciones realmente producidas. El real suma el
 * consumo de producción (que ya incluye la merma) y los ajustes netos del periodo, de modo que la
 * desviación se explica exactamente por {@code wasteQuantity + adjustmentQuantity}.</p>
 */
public record ConsumptionReport(LocalDate from, LocalDate to, List<ProductConsumption> products) {

    public ConsumptionReport {
        products = products == null ? List.of() : List.copyOf(products);
    }

    /** Comparación de un producto; todas las cantidades están en su unidad base. */
    public record ProductConsumption(
            long productId,
            String productName,
            String unit,
            BigDecimal theoreticalQuantity,
            BigDecimal productionQuantity,
            BigDecimal wasteQuantity,
            BigDecimal adjustmentQuantity,
            BigDecimal realQuantity,
            BigDecimal deviation) {}
}
