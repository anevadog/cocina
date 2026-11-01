package com.killa.cocina.modules.inventory.api;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

/** Edición parcial de producto; un campo {@code null} no se modifica (INV-01). */
public record UpdateProductRequest(
        @Size(min = 2, max = 120, message = "debe tener entre 2 y 120 caracteres")
        String name,

        @Positive(message = "debe ser mayor que cero") Long categoryId,

        @DecimalMin(value = "0", message = "no puede ser negativo")
        BigDecimal minStock,

        Boolean allowNegative,

        Boolean active) {}
