package com.killa.cocina.modules.inventory.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.killa.cocina.shared.error.ApiException;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Datos de entrada de compras y ajustes (INV-04, INV-05, D10, D14). */
class StockRegistrationTest {

    private static final Instant NOW = Instant.parse("2026-10-30T10:00:00Z");

    @Test
    @DisplayName("Una compra sin fecha de recepción se registra en el momento actual")
    void laRecepcionPorDefectoEsAhora() {
        PurchaseRegistration registration = purchase(null, null);

        PurchaseRegistration.Schedule schedule = registration.schedule(NOW);

        assertThat(schedule.receivedAt()).isEqualTo(NOW);
        assertThat(schedule.expiresAt()).isNull();
    }

    @Test
    @DisplayName("La recepción no puede ser futura y la caducidad debe ser posterior")
    void validaLasFechas() {
        assertThatThrownBy(() -> purchase(NOW.plusSeconds(1), null).schedule(NOW))
                .isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> purchase(null, NOW.minusSeconds(1)).schedule(NOW))
                .isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> purchase(NOW.minusSeconds(10), NOW.minusSeconds(20)).schedule(NOW))
                .isInstanceOf(ApiException.class);
    }

    @Test
    @DisplayName("La compra exige proveedor, cantidad positiva, unidad y precio no negativo")
    void validaLaCompra() {
        assertThatThrownBy(() -> new PurchaseRegistration(0, BigDecimal.ONE, "kg", BigDecimal.ONE,
                null, null)).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> new PurchaseRegistration(1, BigDecimal.ZERO, "kg", BigDecimal.ONE,
                null, null)).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> new PurchaseRegistration(1, BigDecimal.ONE, " ", BigDecimal.ONE,
                null, null)).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> new PurchaseRegistration(1, BigDecimal.ONE, "kg",
                new BigDecimal("-1"), null, null)).isInstanceOf(ApiException.class);
    }

    @Test
    @DisplayName("Un ajuste solo admite ADJUSTMENT_IN y ADJUSTMENT_OUT")
    void elAjusteSoloAdmiteAjustes() {
        assertThat(new AdjustmentRegistration(MovementType.ADJUSTMENT_OUT, BigDecimal.ONE, "kg",
                "Rotura de saco").reason()).isEqualTo("Rotura de saco");
        assertThatThrownBy(() -> new AdjustmentRegistration(MovementType.PURCHASE_IN, BigDecimal.ONE,
                "kg", "Rotura de saco")).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> new AdjustmentRegistration(null, BigDecimal.ONE, "kg",
                "Rotura de saco")).isInstanceOf(ApiException.class);
    }

    @Test
    @DisplayName("El motivo es obligatorio, se normaliza y mide entre 5 y 200 caracteres")
    void validaElMotivo() {
        assertThat(new AdjustmentRegistration(MovementType.ADJUSTMENT_IN, BigDecimal.ONE, "kg",
                "  Conteo   físico  ").reason()).isEqualTo("Conteo físico");
        assertThatThrownBy(() -> new AdjustmentRegistration(MovementType.ADJUSTMENT_IN,
                BigDecimal.ONE, "kg", "  ")).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> new AdjustmentRegistration(MovementType.ADJUSTMENT_IN,
                BigDecimal.ONE, "kg", "abc")).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> new AdjustmentRegistration(MovementType.ADJUSTMENT_IN,
                BigDecimal.ONE, "kg", "x".repeat(201))).isInstanceOf(ApiException.class);
    }

    private static PurchaseRegistration purchase(Instant receivedAt, Instant expiresAt) {
        return new PurchaseRegistration(1L, BigDecimal.ONE, "kg", new BigDecimal("12.00"),
                receivedAt, expiresAt);
    }
}
