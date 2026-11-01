package com.killa.cocina.modules.recipes.domain;

import com.killa.cocina.shared.error.ApiError;
import com.killa.cocina.shared.error.ApiException;
import com.killa.cocina.shared.error.ErrorCode;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * Límites y normalización de los datos de una receta.
 *
 * <p>Se centralizan aquí para que la API, el servicio de aplicación y las pruebas compartan una
 * única definición de qué es válido (REC-01, REC-02).</p>
 */
final class RecipeLimits {

    static final int MAX_RATIONS = 10_000;

    static final int MAX_PREP_MINUTES = 1_440;

    static final int MAX_INSTRUCTIONS = 4_000;

    static final BigDecimal MAX_WASTE = new BigDecimal("99.99");

    static final BigDecimal DEFAULT_YIELD = new BigDecimal("100.00");

    private static final int WASTE_SCALE = 2;

    private static final int YIELD_SCALE = 2;

    private RecipeLimits() {}

    /** Nombre de receta: se colapsan espacios para que dos variantes no convivan. */
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

    /** Categoría libre: vacía o en blanco se convierte en {@code null} (sin categoría). */
    static String category(String category) {
        if (category == null || category.isBlank()) {
            return null;
        }
        String normalized = category.trim().replaceAll("\\s+", " ");
        if (normalized.length() < 2 || normalized.length() > 80) {
            throw invalid("category", "debe tener entre 2 y 80 caracteres");
        }
        return normalized;
    }

    static int baseRations(int rations) {
        if (rations < 1 || rations > MAX_RATIONS) {
            throw invalid("baseRations", "debe estar entre 1 y " + MAX_RATIONS);
        }
        return rations;
    }

    /** Rendimiento tras cocción (REC-01). Informativo: no altera el coste de los ingredientes. */
    static BigDecimal yieldPct(BigDecimal yieldPct) {
        if (yieldPct == null) {
            return DEFAULT_YIELD;
        }
        if (yieldPct.signum() <= 0 || yieldPct.compareTo(BigDecimal.valueOf(100)) > 0) {
            throw invalid("yieldPct", "debe ser mayor que 0 y como máximo 100");
        }
        return yieldPct.setScale(YIELD_SCALE, RoundingMode.HALF_UP);
    }

    static Integer prepTimeMinutes(Integer minutes) {
        if (minutes == null) {
            return null;
        }
        if (minutes < 1 || minutes > MAX_PREP_MINUTES) {
            throw invalid("prepTimeMinutes", "debe estar entre 1 y " + MAX_PREP_MINUTES);
        }
        return minutes;
    }

    static String instructions(String instructions) {
        if (instructions == null || instructions.isBlank()) {
            return null;
        }
        String normalized = instructions.trim();
        if (normalized.length() > MAX_INSTRUCTIONS) {
            throw invalid("instructions", "no puede superar " + MAX_INSTRUCTIONS + " caracteres");
        }
        return normalized;
    }

    /** Merma porcentual del ingrediente: se suma a la cantidad neta (ADR-033). */
    static BigDecimal waste(BigDecimal waste) {
        if (waste == null) {
            return BigDecimal.ZERO.setScale(WASTE_SCALE);
        }
        if (waste.signum() < 0 || waste.compareTo(MAX_WASTE) > 0) {
            throw invalid("wastePct", "debe estar entre 0 y " + MAX_WASTE.toPlainString());
        }
        return waste.setScale(WASTE_SCALE, RoundingMode.HALF_UP);
    }

    static ApiException invalid(String field, String message) {
        return new ApiException(
                ErrorCode.VALIDATION_ERROR,
                "La petición contiene datos no válidos.",
                List.of(new ApiError.FieldViolation(field, message)));
    }
}
