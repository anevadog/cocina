package com.killa.cocina.modules.inventory.domain;

import com.killa.cocina.shared.error.ApiError;
import com.killa.cocina.shared.error.ApiException;
import com.killa.cocina.shared.error.ErrorCode;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Locale;

/**
 * Aritmética de cantidades y costes del inventario (INV-02, INV-03, INV-09).
 *
 * <p>Una sola definición de escala y redondeo para todo el módulo: cantidades con 3 decimales y
 * costes con 4, siempre {@code HALF_UP}. El redondeo se decide aquí y no en cada servicio, para que
 * dos caminos distintos no den resultados distintos (ADR-021).</p>
 */
public final class Quantities {

    /** Decimales de toda cantidad (numeric(12,3)). */
    public static final int STOCK_SCALE = 3;

    /** Decimales de todo coste (numeric(12,4)). */
    public static final int COST_SCALE = 4;

    /** Redondeo único del módulo. */
    public static final RoundingMode ROUNDING = RoundingMode.HALF_UP;

    private Quantities() {
    }

    /** Normaliza una cantidad a la escala de stock. */
    public static BigDecimal stock(BigDecimal value) {
        return value == null ? null : value.setScale(STOCK_SCALE, ROUNDING);
    }

    /** Normaliza un coste a la escala monetaria. */
    public static BigDecimal cost(BigDecimal value) {
        return value == null ? null : value.setScale(COST_SCALE, ROUNDING);
    }

    /**
     * Convierte una cantidad expresada en {@code unit} a la unidad base del producto.
     *
     * @throws ApiException {@link ErrorCode#UNIT_INCOMPATIBLE} si la unidad no es la base ni está
     *         registrada para ese producto
     */
    public static BigDecimal toBase(Product product, List<UnitConversion> conversions,
            BigDecimal quantity, String unit) {
        if (quantity == null) {
            throw invalid("quantity", "la cantidad es obligatoria");
        }
        if (quantity.signum() <= 0) {
            throw invalid("quantity", "la cantidad debe ser mayor que cero");
        }
        String normalized = unit == null ? "" : unit.trim().toLowerCase(Locale.ROOT);
        if (BaseUnit.fromLabel(normalized).filter(base -> base == product.baseUnit()).isPresent()) {
            return stock(quantity);
        }
        return conversions.stream()
                .filter(conversion -> conversion.unit().equals(normalized))
                .findFirst()
                .map(conversion -> stock(quantity.multiply(conversion.factorToBase())))
                .orElseThrow(() -> incompatible(product, normalized));
    }

    private static ApiException incompatible(Product product, String unit) {
        return new ApiException(ErrorCode.UNIT_INCOMPATIBLE,
                "La unidad '" + unit + "' no es compatible con el producto '" + product.name()
                        + "' (unidad base: " + product.baseUnit().label() + ").");
    }

    static ApiException invalid(String field, String message) {
        return new ApiException(ErrorCode.VALIDATION_ERROR, "La petición contiene datos no válidos.",
                List.of(new ApiError.FieldViolation(field, message)));
    }
}
