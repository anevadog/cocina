package com.killa.cocina.modules.quality.domain;

import com.killa.cocina.modules.inventory.domain.Quantities;
import com.killa.cocina.shared.error.ApiError;
import com.killa.cocina.shared.error.ApiException;
import com.killa.cocina.shared.error.ErrorCode;
import java.math.BigDecimal;
import java.util.List;

/**
 * Alta de una merma (CAL-03, ADR-039).
 *
 * <p>La cantidad se expresa en la unidad base del producto. El coste no viaja en la petición: lo
 * calcula el servicio con el PMP vigente bajo el bloqueo del producto.</p>
 *
 * @param lotId lote concreto a retirar; {@code null} consume los lotes con saldo en orden FIFO
 * @param quantityBase cantidad en unidad base, mayor que cero
 * @param reason motivo libre de 5 a 200 caracteres
 */
public record WasteRegistration(long productId, Long lotId, BigDecimal quantityBase, String reason) {

    /** Longitud mínima del motivo. */
    public static final int MIN_REASON_LENGTH = 5;

    /** Longitud máxima del motivo (coincide con varchar(200)). */
    public static final int MAX_REASON_LENGTH = 200;

    public WasteRegistration {
        if (productId <= 0) {
            throw invalid("productId", "es obligatorio");
        }
        if (lotId != null && lotId <= 0) {
            throw invalid("lotId", "debe ser mayor que cero");
        }
        if (quantityBase == null) {
            throw invalid("quantityBase", "es obligatoria");
        }
        quantityBase = Quantities.stock(quantityBase);
        if (quantityBase.signum() <= 0) {
            throw invalid("quantityBase", "debe ser mayor que cero");
        }
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

    static ApiException invalid(String field, String message) {
        return new ApiException(
                ErrorCode.VALIDATION_ERROR,
                "La petición contiene datos no válidos.",
                List.of(new ApiError.FieldViolation(field, message)));
    }
}
