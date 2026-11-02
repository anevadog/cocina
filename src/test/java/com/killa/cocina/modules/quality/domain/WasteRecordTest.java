package com.killa.cocina.modules.quality.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.killa.cocina.shared.error.ApiException;
import com.killa.cocina.shared.error.ErrorCode;
import java.math.BigDecimal;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Reglas de una merma (CAL-03). */
class WasteRecordTest {

    private static final Instant NOW = Instant.parse("2026-11-02T10:00:00Z");

    @Test
    @DisplayName("La cantidad se normaliza a tres decimales y el motivo se recorta")
    void normalizaCantidadYMotivo() {
        WasteRegistration registration = registration(new BigDecimal("1.2345"), "  Retirada   de   prueba ");

        assertThat(registration.quantityBase()).isEqualByComparingTo("1.235");
        assertThat(registration.reason()).isEqualTo("Retirada de prueba");
    }

    @Test
    @DisplayName("La cantidad debe ser mayor que cero")
    void exigeCantidadPositiva() {
        assertThatThrownBy(() -> registration(BigDecimal.ZERO, "Motivo válido"))
                .isInstanceOfSatisfying(
                        ApiException.class, error -> assertThat(error.code()).isEqualTo(ErrorCode.VALIDATION_ERROR));
        assertThatThrownBy(() -> registration(new BigDecimal("-1"), "Motivo válido"))
                .isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> registration(null, "Motivo válido")).isInstanceOf(ApiException.class);
    }

    @Test
    @DisplayName("El motivo es obligatorio y debe tener entre 5 y 200 caracteres")
    void exigeMotivo() {
        assertThatThrownBy(() -> registration(BigDecimal.ONE, " "))
                .isInstanceOfSatisfying(
                        ApiException.class, error -> assertThat(error.code()).isEqualTo(ErrorCode.VALIDATION_ERROR));
        assertThatThrownBy(() -> registration(BigDecimal.ONE, "mal")).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> registration(BigDecimal.ONE, "x".repeat(201))).isInstanceOf(ApiException.class);

        assertThat(registration(BigDecimal.ONE, "x".repeat(200)).reason()).hasSize(200);
    }

    @Test
    @DisplayName("El producto es obligatorio y el lote, si se indica, positivo")
    void validaProductoYLote() {
        assertThatThrownBy(() -> new WasteRegistration(0L, null, BigDecimal.ONE, "Motivo válido"))
                .isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> new WasteRegistration(1L, 0L, BigDecimal.ONE, "Motivo válido"))
                .isInstanceOf(ApiException.class);
    }

    @Test
    @DisplayName("La cabecera provisional nace sin coste y se completa conservando sus datos")
    void completaLaCabeceraProvisional() {
        WasteRecord provisional = WasteRecord.provisional(
                1L, 2L, 3L, new BigDecimal("1500"), "Retirada de prueba", 7L, NOW.minusSeconds(60), NOW);

        assertThat(provisional.id()).isNull();
        assertThat(provisional.unitCost()).isEqualByComparingTo("0");
        assertThat(provisional.cost()).isEqualByComparingTo("0");

        WasteRecord valued = provisional.withCosts(new BigDecimal("0.0167"), new BigDecimal("25.05"));
        assertThat(valued.quantityBase()).isEqualByComparingTo("1500");
        assertThat(valued.lotId()).isEqualTo(3L);
        assertThat(valued.actorId()).isEqualTo(7L);
        assertThat(valued.unitCost()).isEqualByComparingTo("0.0167");
        assertThat(valued.cost()).isEqualByComparingTo("25.0500");
    }

    @Test
    @DisplayName("Un coste negativo no es válido")
    void rechazaCosteNegativo() {
        WasteRecord provisional = WasteRecord.provisional(1L, 2L, null, BigDecimal.ONE, "Motivo válido", 7L, NOW, NOW);

        assertThatThrownBy(() -> provisional.withCosts(new BigDecimal("-0.01"), BigDecimal.ZERO))
                .isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> provisional.withCosts(BigDecimal.ZERO, new BigDecimal("-0.01")))
                .isInstanceOf(ApiException.class);
    }

    private static WasteRegistration registration(BigDecimal quantity, String reason) {
        return new WasteRegistration(1L, null, quantity, reason);
    }
}
