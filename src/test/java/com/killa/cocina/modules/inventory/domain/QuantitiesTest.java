package com.killa.cocina.modules.inventory.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.killa.cocina.shared.error.ApiException;
import com.killa.cocina.shared.error.ErrorCode;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Aritmética de cantidades y conversión a la unidad base (INV-02, INV-03, INV-09). */
class QuantitiesTest {

    private static final Instant NOW = Instant.parse("2026-10-30T10:00:00Z");

    private static final Product CHICKEN = product("Pollo", BaseUnit.G);

    private static final List<UnitConversion> CONVERSIONS =
            List.of(new UnitConversion("kg", new BigDecimal("1000")));

    @Test
    @DisplayName("0,25 kg se guardan como 250,000 g en la unidad base")
    void convierteLaUnidadDeCompraALaBase() {
        BigDecimal base = Quantities.toBase(CHICKEN, CONVERSIONS, new BigDecimal("0.25"), "kg");

        assertThat(base).isEqualByComparingTo("250");
        assertThat(base.scale()).isEqualTo(Quantities.STOCK_SCALE);
    }

    @Test
    @DisplayName("La propia unidad base no necesita conversión")
    void aceptaLaEtiquetaDeLaUnidadBase() {
        assertThat(Quantities.toBase(CHICKEN, CONVERSIONS, new BigDecimal("500"), " g "))
                .isEqualByComparingTo("500");
    }

    @Test
    @DisplayName("El redondeo es HALF_UP a tres decimales, el mismo en todo el módulo")
    void redondeaConMitadHaciaArriba() {
        BigDecimal base = Quantities.toBase(CHICKEN, CONVERSIONS, new BigDecimal("0.0001235"), "kg");

        assertThat(base).isEqualByComparingTo("0.124");
        assertThat(base.scale()).isEqualTo(3);
    }

    @Test
    @DisplayName("g → ml se rechaza con UNIT_INCOMPATIBLE")
    void rechazaUnaUnidadDeOtraDimension() {
        assertThatThrownBy(
                () -> Quantities.toBase(CHICKEN, List.of(), new BigDecimal("100"), "ml"))
                .isInstanceOf(ApiException.class)
                .satisfies(exception -> assertThat(((ApiException) exception).code())
                        .isEqualTo(ErrorCode.UNIT_INCOMPATIBLE));
    }

    @Test
    @DisplayName("Una cantidad nula o no positiva es un error de validación")
    void rechazaCantidadesNoPositivas() {
        assertThatThrownBy(() -> Quantities.toBase(CHICKEN, CONVERSIONS, BigDecimal.ZERO, "kg"))
                .isInstanceOf(ApiException.class)
                .satisfies(exception -> assertThat(((ApiException) exception).code())
                        .isEqualTo(ErrorCode.VALIDATION_ERROR));
        assertThatThrownBy(() -> Quantities.toBase(CHICKEN, CONVERSIONS, null, "kg"))
                .isInstanceOf(ApiException.class)
                .satisfies(exception -> assertThat(((ApiException) exception).code())
                        .isEqualTo(ErrorCode.VALIDATION_ERROR));
    }

    @Test
    @DisplayName("El coste se normaliza a cuatro decimales")
    void normalizaElCoste() {
        assertThat(Quantities.cost(new BigDecimal("12.34565"))).isEqualByComparingTo("12.3457");
    }

    private static Product product(String name, BaseUnit baseUnit) {
        return new Product(1L, 1L, 1L, name, baseUnit, BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO, false, true, NOW, NOW);
    }
}
