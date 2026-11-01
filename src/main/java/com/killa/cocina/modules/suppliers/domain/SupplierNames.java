package com.killa.cocina.modules.suppliers.domain;

import com.killa.cocina.shared.error.ApiError;
import com.killa.cocina.shared.error.ApiException;
import com.killa.cocina.shared.error.ErrorCode;
import java.util.List;

/** Reglas comunes de nombres y contactos de proveedor. */
final class SupplierNames {

    private SupplierNames() {}

    /** Nombre obligatorio de 2 a 120 caracteres, con espacios interiores colapsados. */
    static String name(String name) {
        if (name == null || name.isBlank()) {
            throw invalid("name", "es obligatorio");
        }
        String normalized = name.trim().replaceAll("\\s+", " ");
        if (normalized.length() < 2 || normalized.length() > 120) {
            throw invalid("name", "debe tener entre 2 y 120 caracteres");
        }
        return normalized;
    }

    /** Contacto opcional (teléfono o correo) de hasta 200 caracteres. */
    static String contact(String contact) {
        if (contact == null || contact.isBlank()) {
            return null;
        }
        String normalized = contact.trim();
        if (normalized.length() > 200) {
            throw invalid("contact", "no puede superar 200 caracteres");
        }
        return normalized;
    }

    static ApiException invalid(String field, String message) {
        return new ApiException(
                ErrorCode.VALIDATION_ERROR,
                "La petición contiene datos no válidos.",
                List.of(new ApiError.FieldViolation(field, message)));
    }
}
