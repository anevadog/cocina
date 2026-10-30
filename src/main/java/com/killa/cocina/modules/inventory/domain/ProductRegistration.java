package com.killa.cocina.modules.inventory.domain;

import com.killa.cocina.shared.error.ApiError;
import com.killa.cocina.shared.error.ApiException;
import com.killa.cocina.shared.error.ErrorCode;
import java.math.BigDecimal;
import java.util.List;

/**
 * Datos para dar de alta un producto (INV-01).
 *
 * <p>Valida en el constructor: si el objeto existe, es coherente.</p>
 */
public record ProductRegistration(String name, long categoryId, BaseUnit baseUnit, BigDecimal minStock,
        boolean allowNegative, List<UnitConversion> conversions) {

    public ProductRegistration {
        name = Names.normalize(name);
        if (categoryId <= 0) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "La petición contiene datos no válidos.",
                    List.of(new ApiError.FieldViolation("categoryId", "la categoría es obligatoria")));
        }
        if (baseUnit == null) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "La petición contiene datos no válidos.",
                    List.of(new ApiError.FieldViolation("baseUnit", "la unidad base es obligatoria")));
        }
        minStock = normalizeMinStock(minStock);
        conversions = Conversions.validate(conversions, baseUnit);
    }

    private static BigDecimal normalizeMinStock(BigDecimal minStock) {
        if (minStock == null) {
            return BigDecimal.ZERO.setScale(Quantities.STOCK_SCALE);
        }
        if (minStock.signum() < 0) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "La petición contiene datos no válidos.",
                    List.of(new ApiError.FieldViolation("minStock", "no puede ser negativo")));
        }
        return Quantities.stock(minStock);
    }
}
