package com.killa.cocina.modules.reporting;

import static org.assertj.core.api.Assertions.assertThat;

import com.killa.cocina.modules.reporting.domain.ExpiringLotSnapshot;
import com.killa.cocina.modules.reporting.domain.ExpiryRiskLine;
import com.killa.cocina.modules.reporting.domain.ExpiryRiskReport;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Reglas del informe de caducidades y valor en riesgo (ECO-06). */
class ExpiryRiskReportTest {

    private static final Instant NOW = Instant.parse("2026-11-02T08:00:00Z");

    @Test
    @DisplayName("Valora cada lote y cuenta los días completos que le quedan")
    void valoraYCuentaDias() {
        ExpiryRiskReport report = ExpiryRiskReport.from(
                7,
                NOW,
                List.of(lot(1, "Yogur", NOW.plus(3, ChronoUnit.DAYS), "1000.000", "0.0050")),
                1,
                new BigDecimal("5.0000"));

        ExpiryRiskLine line = report.lots().getFirst();
        assertThat(line.daysRemaining()).isEqualTo(3);
        assertThat(line.remainingQty()).isEqualByComparingTo("1000.000");
        assertThat(line.unitCost()).isEqualByComparingTo("0.0050");
        assertThat(line.value()).isEqualByComparingTo("5.0000");
        assertThat(report.horizonDays()).isEqualTo(7);
        assertThat(report.generatedAt()).isEqualTo(NOW);
    }

    @Test
    @DisplayName("Ordena por caducidad y, a igualdad, por identificador de lote")
    void ordena() {
        ExpiryRiskReport report = ExpiryRiskReport.from(
                7,
                NOW,
                List.of(
                        lot(3, "Tres", NOW.plus(5, ChronoUnit.DAYS), "1.000", "1.0000"),
                        lot(2, "Dos", NOW.plus(1, ChronoUnit.DAYS), "1.000", "1.0000"),
                        lot(1, "Uno", NOW.plus(1, ChronoUnit.DAYS), "1.000", "1.0000")),
                3,
                new BigDecimal("3.0000"));

        assertThat(report.lots()).extracting(ExpiryRiskLine::lotId).containsExactly(1L, 2L, 3L);
    }

    @Test
    @DisplayName("Los totales cuentan todos los lotes aunque el listado se acote")
    void totalesSobreTodosLosLotes() {
        ExpiryRiskReport report = ExpiryRiskReport.from(
                7,
                NOW,
                List.of(lot(1, "Yogur", NOW.plus(1, ChronoUnit.DAYS), "1.000", "2.0000")),
                12,
                new BigDecimal("24.0000"));

        assertThat(report.returnedLots()).isEqualTo(1);
        assertThat(report.totalLots()).isEqualTo(12);
        assertThat(report.totalValue()).isEqualByComparingTo("24.0000");
    }

    @Test
    @DisplayName("Normaliza cantidad y coste a las escalas del dominio")
    void normalizaEscalas() {
        ExpiryRiskReport report = ExpiryRiskReport.from(
                7,
                NOW,
                List.of(lot(1, "Yogur", NOW.plus(1, ChronoUnit.DAYS), "1.23456", "0.00505")),
                1,
                BigDecimal.ZERO);

        ExpiryRiskLine line = report.lots().getFirst();
        assertThat(line.remainingQty().scale()).isEqualTo(3);
        assertThat(line.unitCost().scale()).isEqualTo(4);
        assertThat(line.value().scale()).isEqualTo(4);
    }

    private static ExpiringLotSnapshot lot(
            long lotId, String productName, Instant expiresAt, String quantity, String unitCost) {
        return new ExpiringLotSnapshot(
                lotId, lotId, productName, expiresAt, new BigDecimal(quantity), new BigDecimal(unitCost));
    }
}
