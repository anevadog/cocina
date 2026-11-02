package com.killa.cocina.modules.reporting.domain;

import com.killa.cocina.modules.inventory.domain.Quantities;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;

/**
 * Informe de productos próximos a caducar con el valor en riesgo (ECO-06).
 *
 * <p>El listado se acota a {@link #MAX_LOTS} lotes para que una consulta no devuelva un inventario
 * entero; {@code totalLots} y {@code totalValue} se calculan sobre <strong>todos</strong> los lotes en
 * riesgo, no solo sobre los devueltos.</p>
 *
 * @param horizonDays días de horizonte consultados
 * @param generatedAt instante del cálculo, en UTC
 * @param returnedLots lotes incluidos en {@code lots}
 * @param totalLots lotes en riesgo en total
 * @param totalValue valor en riesgo de todos ellos
 */
public record ExpiryRiskReport(
        int horizonDays,
        Instant generatedAt,
        int returnedLots,
        long totalLots,
        BigDecimal totalValue,
        List<ExpiryRiskLine> lots) {

    /** Horizonte mínimo aceptado, en días. */
    public static final int MIN_HORIZON_DAYS = 1;

    /** Horizonte máximo aceptado, en días. */
    public static final int MAX_HORIZON_DAYS = 90;

    /** Lotes máximos que devuelve una sola respuesta. */
    public static final int MAX_LOTS = 500;

    public ExpiryRiskReport {
        lots = List.copyOf(lots);
    }

    /** Construye el informe valorando cada lote y contando los días que le quedan. */
    public static ExpiryRiskReport from(
            int horizonDays, Instant now, List<ExpiringLotSnapshot> snapshots, long totalLots, BigDecimal totalValue) {
        List<ExpiryRiskLine> lines = snapshots.stream()
                .sorted(Comparator.comparing(ExpiringLotSnapshot::expiresAt)
                        .thenComparingLong(ExpiringLotSnapshot::lotId))
                .map(snapshot -> new ExpiryRiskLine(
                        snapshot.lotId(),
                        snapshot.productId(),
                        snapshot.productName(),
                        snapshot.expiresAt(),
                        ChronoUnit.DAYS.between(now, snapshot.expiresAt()),
                        Quantities.stock(snapshot.remainingQty()),
                        Quantities.cost(snapshot.unitCost()),
                        Quantities.cost(snapshot.remainingQty().multiply(snapshot.unitCost()))))
                .toList();
        return new ExpiryRiskReport(
                horizonDays,
                now,
                lines.size(),
                totalLots,
                Quantities.cost(totalValue == null ? BigDecimal.ZERO : totalValue),
                lines);
    }
}
