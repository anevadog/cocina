package com.killa.cocina.modules.inventory.api;

import com.killa.cocina.modules.inventory.domain.UnitConversion;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

/**
 * Unidad de compra de un producto y su factor a la unidad base (INV-02).
 *
 * <p>La compatibilidad dimensional no se declara aquí: la decide el producto con su unidad base y
 * el conjunto de conversiones registradas.</p>
 */
public record ConversionRequest(
        @NotBlank(message = "es obligatorio")
        @Size(max = 20, message = "no puede superar 20 caracteres")
        @Pattern(regexp = "[A-Za-z0-9.]{1,20}", message = "solo admite letras, dígitos o punto")
        String unit,

        @NotNull(message = "es obligatorio")
        @DecimalMin(value = "0", inclusive = false, message = "debe ser mayor que cero")
        BigDecimal factorToBase) {

    public UnitConversion toDomain() {
        return new UnitConversion(unit, factorToBase);
    }
}
