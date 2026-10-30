package com.killa.cocina.modules.inventory.domain;

import com.killa.cocina.shared.error.ApiError;
import com.killa.cocina.shared.error.ApiException;
import com.killa.cocina.shared.error.ErrorCode;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Cambios parciales sobre un producto (INV-01). Un campo {@code null} significa «no lo toques».
 *
 * <p>La unidad base no se modifica: cambiarla reinterpretaría todo el histórico de stock y del
 * kardex. Para corregirla se crea otro producto y se ajusta el saldo.</p>
 */
public record ProductModification(String name, Long categoryId, BigDecimal minStock,
        Boolean allowNegative, Boolean active) {

    public ProductModification {
        if (name != null) {
            name = Names.normalize(name);
        }
        if (categoryId != null && categoryId <= 0) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "La petición contiene datos no válidos.",
                    List.of(new ApiError.FieldViolation("categoryId", "no es válido")));
        }
        if (minStock != null) {
            if (minStock.signum() < 0) {
                throw new ApiException(ErrorCode.VALIDATION_ERROR,
                        "La petición contiene datos no válidos.",
                        List.of(new ApiError.FieldViolation("minStock", "no puede ser negativo")));
            }
            minStock = Quantities.stock(minStock);
        }
    }

    public boolean isEmpty() {
        return name == null && categoryId == null && minStock == null && allowNegative == null
                && active == null;
    }

    public Product applyTo(Product product, Instant now) {
        return product.update(name, categoryId, minStock, allowNegative, active, now);
    }
}
