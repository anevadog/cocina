package com.killa.cocina.modules.inventory.domain;

import com.killa.cocina.shared.error.ApiError;
import com.killa.cocina.shared.error.ApiException;
import com.killa.cocina.shared.error.ErrorCode;
import java.math.BigDecimal;
import java.util.List;

/**
 * Ajuste manual de inventario (INV-05, D10/ADR-026).
 *
 * <p>Solo admite {@link MovementType#ADJUSTMENT_IN} y {@link MovementType#ADJUSTMENT_OUT}. El motivo
 * es obligatorio y visible en el kardex: un ajuste sin explicación es un agujero de trazabilidad.</p>
 *
 * @param type dirección del ajuste
 * @param quantity cantidad en {@code unit}; se convierte a unidad base
 * @param unit unidad de compra registrada para el producto
 * @param reason motivo libre de 5 a 200 caracteres
 */
public record AdjustmentRegistration(MovementType type, BigDecimal quantity, String unit, String reason) {

    /** Longitud mínima del motivo. */
    public static final int MIN_REASON_LENGTH = 5;

    /** Longitud máxima del motivo (coincide con varchar(200)). */
    public static final int MAX_REASON_LENGTH = 200;

    public AdjustmentRegistration {
        if (type == null || !type.isAdjustment()) {
            throw invalid("type", "debe ser ADJUSTMENT_IN o ADJUSTMENT_OUT");
        }
        if (quantity == null) {
            throw invalid("quantity", "la cantidad es obligatoria");
        }
        if (quantity.signum() <= 0) {
            throw invalid("quantity", "la cantidad debe ser mayor que cero");
        }
        unit = UnitConversion.normalizeUnit(unit);
        reason = normalizeReason(reason);
    }

    private static String normalizeReason(String reason) {
        if (reason == null || reason.isBlank()) {
            throw invalid("reason", "el motivo es obligatorio");
        }
        String normalized = reason.trim().replaceAll("\\s+", " ");
        if (normalized.length() < MIN_REASON_LENGTH || normalized.length() > MAX_REASON_LENGTH) {
            throw invalid(
                    "reason", "debe tener entre " + MIN_REASON_LENGTH + " y " + MAX_REASON_LENGTH + " caracteres");
        }
        return normalized;
    }

    private static ApiException invalid(String field, String message) {
        return new ApiException(
                ErrorCode.VALIDATION_ERROR,
                "La petición contiene datos no válidos.",
                List.of(new ApiError.FieldViolation(field, message)));
    }
}
