package com.killa.cocina.modules.inventory.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.killa.cocina.shared.error.ApiException;
import java.math.BigDecimal;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Coste medio ponderado (INV-09, ADR-021, ADR-028). */
class ValuationTest {

    private static final Instant NOW = Instant.parse("2026-10-30T10:00:00Z");

    @Test
    @DisplayName("Dos compras a distinto precio dan la media ponderada por cantidad")
    void calculaLaMediaPonderada() {
        Product product = product(new BigDecimal("1000"), new BigDecimal("0.0120"));

        BigDecimal result = Valuation.averageCost(product, new BigDecimal("2000"), new BigDecimal("0.0180"));

        assertThat(result).isEqualByComparingTo("0.0160");
        assertThat(result.scale()).isEqualTo(4);
    }

    @Test
    @DisplayName("Con saldo cero o negativo la entrada fija el PMP (D13)")
    void sinSaldoPositivoLaEntradaFijaElCoste() {
        assertThat(Valuation.averageCost(
                        product(BigDecimal.ZERO, BigDecimal.ZERO), new BigDecimal("500"), new BigDecimal("0.0200")))
                .isEqualByComparingTo("0.0200");
        assertThat(Valuation.averageCost(
                        product(new BigDecimal("-100"), new BigDecimal("0.0100")),
                        new BigDecimal("500"),
                        new BigDecimal("0.0200")))
                .isEqualByComparingTo("0.0200");
    }

    @Test
    @DisplayName("El redondeo del PMP es HALF_UP a cuatro decimales")
    void redondeaElPmp() {
        Product product = product(new BigDecimal("1000"), new BigDecimal("0.0123"));

        BigDecimal result = Valuation.averageCost(product, new BigDecimal("1000"), new BigDecimal("0.0124"));

        assertThat(result).isEqualByComparingTo("0.0124");
        assertThat(result.scale()).isEqualTo(4);
    }

    @Test
    @DisplayName("Una entrada sin cantidad positiva no es válida")
    void rechazaCantidadNoPositiva() {
        assertThatThrownBy(() ->
                        Valuation.averageCost(product(BigDecimal.TEN, BigDecimal.ONE), BigDecimal.ZERO, BigDecimal.ONE))
                .isInstanceOf(ApiException.class);
    }

    private static Product product(BigDecimal stock, BigDecimal avgCost) {
        return new Product(1L, 1L, 1L, "Harina", BaseUnit.G, BigDecimal.ZERO, stock, avgCost, false, true, NOW, NOW);
    }
}
