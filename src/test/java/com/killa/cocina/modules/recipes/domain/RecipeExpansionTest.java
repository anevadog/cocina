package com.killa.cocina.modules.recipes.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.killa.cocina.shared.error.ApiException;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Aritmética de expansión, merma y coste (REC-02, REC-03, REC-04, ADR-033).
 *
 * <p>Los casos están calculados a mano: la gracia es que el redondeo ocurra una sola vez por
 * magnitud, no que el test repita la implementación.</p>
 */
class RecipeExpansionTest {

    private static final long ESTABLISHMENT = 1L;

    @Test
    @DisplayName("REC-03: 250 g por ración escalados a 3 raciones son 750 g")
    void escalaCantidadesSinMerma() {
        assertThat(RecipeExpansion.consumption(new BigDecimal("250"), BigDecimal.ZERO, 3))
                .isEqualByComparingTo("750.000");
        assertThat(RecipeExpansion.consumption(new BigDecimal("250"), null, 1)).isEqualByComparingTo("250.000");
    }

    @Test
    @DisplayName("La merma se suma a la cantidad neta, no se descuenta del bruto")
    void aplicaLaMermaDeFormaAditiva() {
        assertThat(RecipeExpansion.consumption(new BigDecimal("250"), new BigDecimal("10"), 1))
                .isEqualByComparingTo("275.000");
        assertThat(RecipeExpansion.consumption(new BigDecimal("250"), new BigDecimal("10"), 3))
                .isEqualByComparingTo("825.000");
        assertThat(RecipeExpansion.consumption(new BigDecimal("1"), new BigDecimal("99.99"), 1))
                .isEqualByComparingTo("2.000");
    }

    @Test
    @DisplayName("La cantidad se redondea una sola vez, al final del escalado")
    void redondeaUnaSolaVez() {
        // 1 × 1,3333 × 3 = 3,9999 → 4,000. Redondear por ración daría 1,333 × 3 = 3,999.
        assertThat(RecipeExpansion.consumption(new BigDecimal("1"), new BigDecimal("33.33"), 3))
                .isEqualByComparingTo("4.000");
        // Y a 3 decimales, no a más: 1 g × 1,005 / 3 raciones no arrastra precisión.
        assertThat(RecipeExpansion.consumption(new BigDecimal("1"), new BigDecimal("0.05"), 7))
                .isEqualByComparingTo("7.004");
    }

    @Test
    @DisplayName("El coste por ración es la suma de cantidad consumida por coste unitario")
    void sumaCantidadPorCosteUnitario() {
        RecipeItem flour = item(10L, "250", "10", 0);
        RecipeItem salt = item(11L, "3", "0", 1);

        RecipeCost cost = RecipeExpansion.costPerRation(
                List.of(flour, salt), Map.of(10L, new BigDecimal("0.0100"), 11L, new BigDecimal("0.0010")));

        assertThat(cost.complete()).isTrue();
        assertThat(cost.perRation()).isEqualByComparingTo("2.7530");
        assertThat(RecipeExpansion.batchCost(cost.perRation(), 4)).isEqualByComparingTo("11.0120");
        assertThat(RecipeExpansion.batchCost(cost.perRation(), 3)).isEqualByComparingTo("8.2590");
    }

    @Test
    @DisplayName("Sin coste de algún ingrediente no se publica una cifra a medias")
    void noInventaCosteSiFaltaUnIngrediente() {
        RecipeCost cost = RecipeExpansion.costPerRation(
                List.of(item(10L, "250", "0", 0), item(11L, "3", "0", 1)), Map.of(10L, new BigDecimal("0.0100")));

        assertThat(cost.complete()).isFalse();
        assertThat(cost.perRation()).isNull();
        assertThat(cost.missingProducts()).containsExactly(11L);
        assertThat(RecipeExpansion.batchCost(null, 4)).isNull();
    }

    @Test
    @DisplayName("El coste de la línea se redondea a 4 decimales")
    void redondeaElCosteDeLaLinea() {
        assertThat(RecipeExpansion.lineCost(new BigDecimal("275.000"), new BigDecimal("0.0100")))
                .isEqualByComparingTo("2.7500");
        assertThat(RecipeExpansion.lineCost(new BigDecimal("1.000"), new BigDecimal("0.00005")))
                .isEqualByComparingTo("0.0001");
        assertThat(RecipeExpansion.lineCost(new BigDecimal("1.000"), null)).isNull();
    }

    @Test
    @DisplayName("El escalado exige entre 1 y 10000 raciones")
    void acotaLasRaciones() {
        assertThat(RecipeExpansion.rations(1)).isEqualTo(1);
        assertThat(RecipeExpansion.rations(10_000)).isEqualTo(10_000);
        assertThatThrownBy(() -> RecipeExpansion.rations(0)).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> RecipeExpansion.rations(-1)).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> RecipeExpansion.rations(10_001)).isInstanceOf(ApiException.class);
    }

    private static RecipeItem item(long productId, String quantity, String waste, int order) {
        return new RecipeItem(
                null, ESTABLISHMENT, 5L, productId, new BigDecimal(quantity), new BigDecimal(waste), order);
    }
}
