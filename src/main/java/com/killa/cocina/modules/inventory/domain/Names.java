package com.killa.cocina.modules.inventory.domain;

import com.killa.cocina.shared.error.ApiError;
import com.killa.cocina.shared.error.ApiException;
import com.killa.cocina.shared.error.ErrorCode;
import java.util.List;

/** Reglas comunes de nombres del catálogo. */
final class Names {

    private Names() {
    }

    /**
     * Normaliza y valida el nombre de una categoría, producto o proveedor.
     *
     * <p>Se colapsan los espacios interiores para que «Tomate  frito» y «Tomate frito» no convivan
     * como dos recursos distintos.</p>
     */
    static String normalize(String name) {
        return normalize(name, 120);
    }

    /** Igual que {@link #normalize(String)} con un máximo distinto (las categorías admiten 80). */
    static String normalize(String name, int maxLength) {
        if (name == null || name.isBlank()) {
            throw invalid("name", "es obligatorio");
        }
        String normalized = name.trim().replaceAll("\\s+", " ");
        if (normalized.length() < 2 || normalized.length() > maxLength) {
            throw invalid("name", "debe tener entre 2 y " + maxLength + " caracteres");
        }
        return normalized;
    }

    private static ApiException invalid(String field, String message) {
        return new ApiException(ErrorCode.VALIDATION_ERROR, "La petición contiene datos no válidos.",
                List.of(new ApiError.FieldViolation(field, message)));
    }
}
