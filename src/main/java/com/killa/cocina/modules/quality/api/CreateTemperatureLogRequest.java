package com.killa.cocina.modules.quality.api;

import com.killa.cocina.modules.quality.domain.TemperatureKind;
import com.killa.cocina.modules.quality.domain.TemperatureLogRegistration;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;

/**
 * Alta de una lectura de temperatura (CAL-02).
 *
 * <p>El rango viaja en cada lectura. {@code recordedAt} es opcional y nunca puede ser futuro.</p>
 */
public record CreateTemperatureLogRequest(
        @NotNull(message = "es obligatorio") TemperatureKind kind,

        @NotBlank(message = "es obligatorio") @Size(min = 2, max = 80, message = "debe tener entre 2 y 80 caracteres")
        String equipment,

        @NotNull(message = "es obligatorio")
        @DecimalMin(value = "-999.99", message = "debe estar entre -999.99 y 999.99")
        @DecimalMax(value = "999.99", message = "debe estar entre -999.99 y 999.99")
        BigDecimal valueC,

        @NotNull(message = "es obligatorio")
        @DecimalMin(value = "-999.99", message = "debe estar entre -999.99 y 999.99")
        @DecimalMax(value = "999.99", message = "debe estar entre -999.99 y 999.99")
        BigDecimal minC,

        @NotNull(message = "es obligatorio")
        @DecimalMin(value = "-999.99", message = "debe estar entre -999.99 y 999.99")
        @DecimalMax(value = "999.99", message = "debe estar entre -999.99 y 999.99")
        BigDecimal maxC,

        Instant recordedAt) {

    public TemperatureLogRegistration toRegistration() {
        return new TemperatureLogRegistration(kind, equipment, valueC, minC, maxC, recordedAt);
    }
}
