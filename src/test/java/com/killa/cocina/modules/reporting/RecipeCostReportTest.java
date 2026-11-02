package com.killa.cocina.modules.reporting;

import static org.assertj.core.api.Assertions.assertThat;

import com.killa.cocina.modules.reporting.domain.RecipeCostLine;
import com.killa.cocina.modules.reporting.domain.RecipeCostReport;
import com.killa.cocina.modules.reporting.domain.RecipeCostSnapshot;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Reglas del informe de costes por versión publicada (ECO-01). */
class RecipeCostReportTest {

    private static final Instant PUBLISHED = Instant.parse("2026-10-01T10:00:00Z");

    @Test
    @DisplayName("Ordena por receta y número de versión, y cuenta recetas distintas")
    void ordenaYCuenta() {
        RecipeCostReport report = RecipeCostReport.from(List.of(
                snapshot(2, "Bacalao", 2, "0.6000", 2),
                snapshot(1, "Arroz", 1, "0.1000", 1),
                snapshot(1, "Arroz", 2, "0.1500", 2)));

        assertThat(report.recipeCount()).isEqualTo(2);
        assertThat(report.versionCount()).isEqualTo(3);
        assertThat(report.versions())
                .extracting(RecipeCostLine::recipeName, RecipeCostLine::versionNumber)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("Arroz", 1),
                        org.assertj.core.groups.Tuple.tuple("Arroz", 2),
                        org.assertj.core.groups.Tuple.tuple("Bacalao", 2));
    }

    @Test
    @DisplayName("La primera versión no tiene variación")
    void primeraVersionSinVariacion() {
        RecipeCostReport report = RecipeCostReport.from(List.of(snapshot(1, "Arroz", 1, "0.1000", 1)));

        RecipeCostLine line = report.versions().getFirst();
        assertThat(line.previousCostPerRation()).isNull();
        assertThat(line.variationAmount()).isNull();
        assertThat(line.variationPct()).isNull();
    }

    @Test
    @DisplayName("Calcula la variación y el porcentaje frente a la versión anterior")
    void calculaVariacion() {
        RecipeCostReport report = RecipeCostReport.from(List.of(
                snapshot(1, "Arroz", 1, "0.1000", 1),
                snapshot(1, "Arroz", 2, "0.1500", 2),
                snapshot(1, "Arroz", 3, "0.1200", 3)));

        assertThat(report.versions().get(1).previousCostPerRation()).isEqualByComparingTo("0.1000");
        assertThat(report.versions().get(1).variationAmount()).isEqualByComparingTo("0.0500");
        assertThat(report.versions().get(1).variationPct()).isEqualByComparingTo("50.00");

        assertThat(report.versions().get(2).previousCostPerRation()).isEqualByComparingTo("0.1500");
        assertThat(report.versions().get(2).variationAmount()).isEqualByComparingTo("-0.0300");
        assertThat(report.versions().get(2).variationPct()).isEqualByComparingTo("-20.00");
    }

    @Test
    @DisplayName("Sin coste anterior no se divide por cero: hay importe pero no porcentaje")
    void costeAnteriorCero() {
        RecipeCostReport report = RecipeCostReport.from(
                List.of(snapshot(1, "Arroz", 1, "0.0000", 1), snapshot(1, "Arroz", 2, "0.1500", 2)));

        assertThat(report.versions().get(1).variationAmount()).isEqualByComparingTo("0.1500");
        assertThat(report.versions().get(1).variationPct()).isNull();
    }

    @Test
    @DisplayName("La variación se calcula por receta, no contra la receta anterior del informe")
    void variacionPorReceta() {
        RecipeCostReport report = RecipeCostReport.from(
                List.of(snapshot(1, "Arroz", 1, "0.1000", 1), snapshot(2, "Bacalao", 1, "2.0000", 1)));

        assertThat(report.versions()).allSatisfy(line -> {
            assertThat(line.previousCostPerRation()).isNull();
            assertThat(line.variationPct()).isNull();
        });
    }

    private static RecipeCostSnapshot snapshot(
            long recipeId, String recipeName, int versionNumber, String costPerRation, int baseRations) {
        return new RecipeCostSnapshot(
                recipeId,
                recipeName,
                recipeId * 100 + versionNumber,
                versionNumber,
                baseRations,
                new BigDecimal(costPerRation).multiply(BigDecimal.valueOf(baseRations)),
                new BigDecimal(costPerRation),
                PUBLISHED);
    }
}
