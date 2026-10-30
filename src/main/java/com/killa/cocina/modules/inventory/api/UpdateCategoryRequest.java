package com.killa.cocina.modules.inventory.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Renombrado de categoría de producto (INV-01). */
public record UpdateCategoryRequest(
        @NotBlank(message = "es obligatorio")
        @Size(min = 2, max = 80, message = "debe tener entre 2 y 80 caracteres")
        String name) {
}
