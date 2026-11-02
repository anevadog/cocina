package com.killa.cocina.modules.reporting.api;

import com.killa.cocina.modules.reporting.domain.ExpiryRiskReport;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Productos próximos a caducar con el valor en riesgo (ECO-06).
 *
 * <p>{@code totalLots} y {@code totalValue} cuentan todos los lotes en riesgo, aunque {@code lots} se
 * limite a 500.</p>
 */
public record ExpiryRiskReportResponse(
        int horizonDays,
        Instant generatedAt,
        int returnedLots,
        long totalLots,
        BigDecimal totalValue,
        List<ExpiryRiskLineResponse> lots) {

    public static ExpiryRiskReportResponse from(ExpiryRiskReport report) {
        return new ExpiryRiskReportResponse(
                report.horizonDays(),
                report.generatedAt(),
                report.returnedLots(),
                report.totalLots(),
                report.totalValue(),
                report.lots().stream().map(ExpiryRiskLineResponse::from).toList());
    }
}
