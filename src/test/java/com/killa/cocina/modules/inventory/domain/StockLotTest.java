package com.killa.cocina.modules.inventory.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Lotes: saldo, valor y caducidad (INV-04, CAL-01, ADR-025). */
class StockLotTest {

    private static final Instant NOW = Instant.parse("2026-10-30T10:00:00Z");

    @Test
    @DisplayName("El valor del lote es saldo restante por coste, con cuatro decimales")
    void calculaElValorDelSaldo() {
        StockLot lot = lot(NOW.plus(Duration.ofDays(5)), new BigDecimal("0.0120"),
                new BigDecimal("1000"));

        assertThat(lot.value()).isEqualByComparingTo("12.0000");
        assertThat(lot.value().scale()).isEqualTo(4);
    }

    @Test
    @DisplayName("Un lote está caducado desde su fecha y entra en aviso dentro de la ventana")
    void evaluaLaCaducidad() {
        StockLot fresh = lot(NOW.plus(Duration.ofDays(10)), BigDecimal.ONE, new BigDecimal("100"));
        StockLot soon = lot(NOW.plus(Duration.ofDays(2)), BigDecimal.ONE, new BigDecimal("100"));
        StockLot expired = lot(NOW.minus(Duration.ofHours(1)), BigDecimal.ONE, new BigDecimal("100"));

        assertThat(fresh.isExpired(NOW)).isFalse();
        assertThat(fresh.expiresWithin(NOW, Duration.ofDays(3))).isFalse();
        assertThat(soon.expiresWithin(NOW, Duration.ofDays(3))).isTrue();
        assertThat(expired.isExpired(NOW)).isTrue();
        assertThat(expired.expiresWithin(NOW, Duration.ofDays(3))).isTrue();
    }

    @Test
    @DisplayName("Un lote sin caducidad nunca caduca ni genera aviso")
    void sinFechaNoCaduca() {
        StockLot lot = lot(null, BigDecimal.ONE, new BigDecimal("100"));

        assertThat(lot.isExpired(NOW)).isFalse();
        assertThat(lot.expiresWithin(NOW, Duration.ofDays(3))).isFalse();
    }

    @Test
    @DisplayName("Consumir descuenta el saldo y no admite superar lo que queda")
    void consumeSinSuperarElSaldo() {
        StockLot lot = lot(null, new BigDecimal("0.5000"), new BigDecimal("1000"));

        StockLot consumed = lot.consume(new BigDecimal("250"));

        assertThat(consumed.remainingQty()).isEqualByComparingTo("750");
        assertThat(lot.remainingQty()).isEqualByComparingTo("1000");
        assertThatThrownBy(() -> lot.consume(new BigDecimal("1000.001")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static StockLot lot(Instant expiresAt, BigDecimal unitCost, BigDecimal quantity) {
        return StockLot.create(1L, 2L, 3L, NOW, expiresAt, unitCost, quantity, NOW);
    }
}
