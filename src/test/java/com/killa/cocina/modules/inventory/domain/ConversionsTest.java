package com.killa.cocina.modules.inventory.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.killa.cocina.shared.error.ApiException;
import com.killa.cocina.shared.error.ErrorCode;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Validación del conjunto de conversiones de un producto (INV-02, D5). */
class ConversionsTest {

    @Test
    @DisplayName("Una unidad de compra conocida y de la misma dimensión se acepta")
    void aceptaUnaConversionCompatible() {
        List<UnitConversion> result =
                Conversions.validate(List.of(new UnitConversion("KG", new BigDecimal("1000"))), BaseUnit.G);

        assertThat(result).containsExactly(new UnitConversion("kg", new BigDecimal("1000")));
    }

    @Test
    @DisplayName("Una etiqueta propia del producto, como «caja», se acepta")
    void aceptaUnaUnidadPropia() {
        assertThat(Conversions.validate(List.of(new UnitConversion("caja", new BigDecimal("12000"))), BaseUnit.G))
                .hasSize(1);
    }

    @Test
    @DisplayName("La unidad base no se declara como conversión")
    void rechazaLaUnidadBaseComoConversion() {
        assertThatThrownBy(() -> Conversions.validate(List.of(new UnitConversion("g", BigDecimal.ONE)), BaseUnit.G))
                .isInstanceOf(ApiException.class)
                .satisfies(exception ->
                        assertThat(((ApiException) exception).code()).isEqualTo(ErrorCode.VALIDATION_ERROR));
    }

    @Test
    @DisplayName("Declarar litros en un producto de peso devuelve UNIT_INCOMPATIBLE")
    void rechazaUnidadesDeOtraDimension() {
        assertThatThrownBy(() ->
                        Conversions.validate(List.of(new UnitConversion("l", new BigDecimal("1000"))), BaseUnit.G))
                .isInstanceOf(ApiException.class)
                .satisfies(exception ->
                        assertThat(((ApiException) exception).code()).isEqualTo(ErrorCode.UNIT_INCOMPATIBLE));
    }

    @Test
    @DisplayName("Dos conversiones con la misma etiqueta se rechazan")
    void rechazaDuplicados() {
        assertThatThrownBy(() -> Conversions.validate(
                        List.of(
                                new UnitConversion("kg", new BigDecimal("1000")),
                                new UnitConversion("kg", new BigDecimal("1000"))),
                        BaseUnit.G))
                .isInstanceOf(ApiException.class)
                .satisfies(exception ->
                        assertThat(((ApiException) exception).code()).isEqualTo(ErrorCode.VALIDATION_ERROR));
    }

    @Test
    @DisplayName("El factor debe ser positivo y admitir como máximo 6 decimales")
    void validaElFactor() {
        assertThatThrownBy(() -> new UnitConversion("kg", BigDecimal.ZERO)).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> new UnitConversion("kg", new BigDecimal("-1"))).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> new UnitConversion("kg", new BigDecimal("1.0000001")))
                .isInstanceOf(ApiException.class);

        UnitConversion conversion = new UnitConversion(" kg ", new BigDecimal("1000"));
        assertThat(conversion.unit()).isEqualTo("kg");
        assertThat(conversion.factorToBase().scale()).isEqualTo(UnitConversion.MAX_FACTOR_SCALE);
    }
}
