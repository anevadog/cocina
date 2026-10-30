package com.killa.cocina.modules.inventory.domain;

import com.killa.cocina.shared.error.ApiError;
import com.killa.cocina.shared.error.ApiException;
import com.killa.cocina.shared.error.ErrorCode;
import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Unidad de compra de un producto con su factor hacia la unidad base (INV-02).
 *
 * <p>El factor es por producto: en un producto de peso, «kg» vale 1000; en uno de volumen, «l» vale
 * 1000. Que la unidad no exista para ese producto es lo que impide convertir gramos en mililitros.</p>
 *
 * @param unit etiqueta en minúsculas, por ejemplo {@code kg}
 * @param factorToBase cuántas unidades base contiene una unidad de compra
 */
public record UnitConversion(String unit, BigDecimal factorToBase) {

    private static final Pattern UNIT_FORMAT = Pattern.compile("[a-z0-9.]{1,20}");

    /** Máximo de decimales admitido en el factor (coincide con numeric(18,6)). */
    public static final int MAX_FACTOR_SCALE = 6;

    public UnitConversion {
        unit = normalizeUnit(unit);
        factorToBase = normalizeFactor(factorToBase, unit);
    }

    /** Normaliza y valida la etiqueta de una unidad de compra (también la usan los registros). */
    static String normalizeUnit(String unit) {
        if (unit == null || unit.isBlank()) {
            throw invalid("unit", "la unidad es obligatoria");
        }
        String normalized = unit.trim().toLowerCase(Locale.ROOT);
        if (!UNIT_FORMAT.matcher(normalized).matches()) {
            throw invalid("unit",
                    "debe tener entre 1 y 20 caracteres: letras minúsculas, dígitos o punto");
        }
        return normalized;
    }

    private static BigDecimal normalizeFactor(BigDecimal factor, String unit) {
        if (factor == null) {
            throw invalid("factorToBase", "el factor es obligatorio");
        }
        if (factor.signum() <= 0) {
            throw invalid("factorToBase", "el factor debe ser mayor que cero");
        }
        if (factor.stripTrailingZeros().scale() > MAX_FACTOR_SCALE) {
            throw invalid("factorToBase", "el factor admite como máximo 6 decimales");
        }
        return factor.setScale(MAX_FACTOR_SCALE, Quantities.ROUNDING);
    }

    static ApiException invalid(String field, String message) {
        return new ApiException(ErrorCode.VALIDATION_ERROR, "La petición contiene datos no válidos.",
                List.of(new ApiError.FieldViolation(field, message)));
    }
}
