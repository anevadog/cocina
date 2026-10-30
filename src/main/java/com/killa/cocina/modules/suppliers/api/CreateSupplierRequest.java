package com.killa.cocina.modules.suppliers.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Alta de proveedor. */
public record CreateSupplierRequest(
        @NotBlank(message = "es obligatorio")
        @Size(min = 2, max = 120, message = "debe tener entre 2 y 120 caracteres")
        String name,

        @Size(max = 200, message = "no puede superar 200 caracteres")
        String contact) {
}
