package com.killa.cocina.modules.production.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.killa.cocina.shared.error.ApiException;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Agregación de necesidades de un plan (PRD-02, ADR-036).
 *
 * <p>La prueba central es que el total se calcula con las cantidades exactas y se redondea una sola
 * vez por producto: redondear cada línea daría un número distinto.</p>
 */
class ProductionExpansionTest {

    @Test
    @DisplayName("PRD-02: suma las líneas exactas y redondea una sola vez por producto")
    void agregaYRedondeaUnaSolaVez() {
        // Cada línea necesita 0,001 g × 1,5 = 0,0015 g. Dos líneas son 0,0030 → 0,003.
        // Si se redondeara cada línea primero: 0,002 + 0,002 = 0,004.
        ProductionCompositionItem item =
                new ProductionCompositionItem(10L, new BigDecimal("0.001"), new BigDecimal("50"));
        Map<Long, BigDecimal> totals = ProductionExpansion.consumptionByProduct(List.of(
                new ProductionExpansion.PlanLine(List.of(item), 1),
                new ProductionExpansion.PlanLine(List.of(item), 1)));

        assertThat(totals).containsOnlyKeys(10L);
        assertThat(totals.get(10L)).isEqualByComparingTo("0.003");
    }

    @Test
    @DisplayName("Aplica merma y raciones antes de agregar por producto")
    void aplicaMermaYRaciones() {
        ProductionCompositionItem flour =
                new ProductionCompositionItem(10L, new BigDecimal("100"), new BigDecimal("10"));
        ProductionCompositionItem salt = new ProductionCompositionItem(11L, new BigDecimal("1.5"), BigDecimal.ZERO);

        Map<Long, BigDecimal> totals = ProductionExpansion.consumptionByProduct(
                List.of(new ProductionExpansion.PlanLine(List.of(flour, salt), 2)));

        assertThat(totals.get(10L)).isEqualByComparingTo("220.000");
        assertThat(totals.get(11L)).isEqualByComparingTo("3.000");
    }

    @Test
    @DisplayName("Un plan sin ítems no inventa necesidades")
    void unPlanVacioNoTieneNecesidades() {
        assertThat(ProductionExpansion.consumptionByProduct(List.of())).isEmpty();
    }

    @Test
    @DisplayName("Una línea con raciones fuera de rango se rechaza")
    void acotaLasRacionesDeLaLinea() {
        ProductionCompositionItem item = new ProductionCompositionItem(10L, BigDecimal.ONE, BigDecimal.ZERO);

        assertThatThrownBy(() -> ProductionExpansion.consumptionByProduct(
                        List.of(new ProductionExpansion.PlanLine(List.of(item), 0))))
                .isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> ProductionExpansion.consumptionByProduct(
                        List.of(new ProductionExpansion.PlanLine(List.of(item), 10_001))))
                .isInstanceOf(ApiException.class);
    }
}
