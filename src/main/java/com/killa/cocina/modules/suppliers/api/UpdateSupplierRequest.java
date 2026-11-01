package com.killa.cocina.modules.suppliers.api;

import jakarta.validation.constraints.Size;

/** Edición parcial de proveedor; un campo {@code null} no se modifica. */
public record UpdateSupplierRequest(
        @Size(min = 2, max = 120, message = "debe tener entre 2 y 120 caracteres")
        String name,

        @Size(max = 200, message = "no puede superar 200 caracteres")
        String contact,

        Boolean active) {}
