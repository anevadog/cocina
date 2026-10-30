package com.killa.cocina.modules.inventory.domain;

import com.killa.cocina.shared.error.ApiError;
import com.killa.cocina.shared.error.ApiException;
import com.killa.cocina.shared.error.ErrorCode;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Entrada de compra con lote (INV-04, D14).
 *
 * @param supplierId proveedor que suministra el lote
 * @param quantity cantidad en {@code unit}; se convierte a unidad base
 * @param unit unidad de compra registrada para el producto
 * @param unitPrice precio por unidad de compra (EUR), nunca por unidad base
 * @param receivedAt instante de recepción; por defecto ahora y nunca futuro
 * @param expiresAt caducidad opcional; si existe, posterior a la recepción
 */
public record PurchaseRegistration(long supplierId, BigDecimal quantity, String unit,
        BigDecimal unitPrice, Instant receivedAt, Instant expiresAt) {

    public PurchaseRegistration {
        if (supplierId <= 0) {
            throw invalid("supplierId", "el proveedor es obligatorio");
        }
        if (quantity == null) {
            throw invalid("quantity", "la cantidad es obligatoria");
        }
        if (quantity.signum() <= 0) {
            throw invalid("quantity", "la cantidad debe ser mayor que cero");
        }
        unit = UnitConversion.normalizeUnit(unit);
        if (unitPrice == null) {
            throw invalid("unitPrice", "el precio es obligatorio");
        }
        if (unitPrice.signum() < 0) {
            throw invalid("unitPrice", "no puede ser negativo");
        }
    }

    /** Recepción y caducidad ya resueltas y validadas contra el reloj del sistema. */
    public record Schedule(Instant receivedAt, Instant expiresAt) {
    }

    public Schedule schedule(Instant now) {
        Instant received = receivedAt == null ? now : receivedAt;
        if (received.isAfter(now)) {
            throw invalid("receivedAt", "no puede ser futura");
        }
        if (expiresAt != null && !expiresAt.isAfter(received)) {
            throw invalid("expiresAt", "debe ser posterior a la recepción");
        }
        return new Schedule(received, expiresAt);
    }

    private static ApiException invalid(String field, String message) {
        return new ApiException(ErrorCode.VALIDATION_ERROR, "La petición contiene datos no válidos.",
                List.of(new ApiError.FieldViolation(field, message)));
    }
}
