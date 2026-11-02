package com.killa.cocina.modules.production.api;

import com.killa.cocina.modules.production.domain.ConsumptionReport;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Consumo teórico frente a real por producto (ECO-03).
 *
 * <p>{@code realQuantity = productionQuantity + adjustmentQuantity} y
 * {@code deviation = realQuantity − theoreticalQuantity = wasteQuantity + adjustmentQuantity}.</p>
 */
public record ConsumptionReportResponse(LocalDate from, LocalDate to, List<ProductConsumptionResponse> products) {

    public static ConsumptionReportResponse from(ConsumptionReport report) {
        return new ConsumptionReportResponse(
                report.from(),
                report.to(),
                report.products().stream().map(ProductConsumptionResponse::from).toList());
    }

    /** Comparación de un producto, en su unidad base. */
    public record ProductConsumptionResponse(
            long productId,
            String productName,
            String unit,
            BigDecimal theoreticalQuantity,
            BigDecimal productionQuantity,
            BigDecimal wasteQuantity,
            BigDecimal adjustmentQuantity,
            BigDecimal realQuantity,
            BigDecimal deviation) {

        static ProductConsumptionResponse from(ConsumptionReport.ProductConsumption line) {
            return new ProductConsumptionResponse(
                    line.productId(),
                    line.productName(),
                    line.unit(),
                    line.theoreticalQuantity(),
                    line.productionQuantity(),
                    line.wasteQuantity(),
                    line.adjustmentQuantity(),
                    line.realQuantity(),
                    line.deviation());
        }
    }
}
