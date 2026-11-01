package com.killa.cocina.modules.inventory.api;

import com.killa.cocina.modules.inventory.domain.BaseUnit;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.List;

/**
 * Alta de producto (INV-01, INV-02).
 *
 * <p>La unidad base no se puede cambiar después: reinterpreta todo el stock histórico.</p>
 */
public record CreateProductRequest(
        @NotBlank(message = "es obligatorio") @Size(min = 2, max = 120, message = "debe tener entre 2 y 120 caracteres")
        String name,

        @NotNull(message = "es obligatorio") @Positive(message = "debe ser mayor que cero")
        Long categoryId,

        @NotNull(message = "es obligatorio") BaseUnit baseUnit,

        @DecimalMin(value = "0", message = "no puede ser negativo")
        BigDecimal minStock,

        Boolean allowNegative,

        List<@Valid ConversionRequest> conversions) {

    public boolean allowsNegative() {
        return Boolean.TRUE.equals(allowNegative);
    }

    public List<ConversionRequest> conversionList() {
        return conversions == null ? List.of() : conversions;
    }
}
