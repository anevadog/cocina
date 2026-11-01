package com.killa.cocina.modules.inventory.domain;

import com.killa.cocina.shared.error.ApiError;
import com.killa.cocina.shared.error.ApiException;
import com.killa.cocina.shared.error.ErrorCode;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Validación del conjunto de conversiones de un producto (INV-02).
 *
 * <p>No se admite la etiqueta de una unidad base (sería una conversión de factor 1 implícita) ni dos
 * conversiones con la misma etiqueta. Se usa tanto al crear el producto como al reemplazar sus
 * conversiones, para que ambos caminos apliquen la misma regla.</p>
 */
public final class Conversions {

    private Conversions() {}

    public static List<UnitConversion> validate(List<UnitConversion> conversions, BaseUnit baseUnit) {
        if (conversions == null || conversions.isEmpty()) {
            return List.of();
        }
        Set<String> seen = new HashSet<>();
        for (UnitConversion conversion : conversions) {
            if (BaseUnit.fromLabel(conversion.unit()).isPresent()) {
                throw invalid(
                        "la unidad '" + conversion.unit() + "' es una unidad base y no se declara como conversión");
            }
            BaseUnit.dimensionOf(conversion.unit())
                    .filter(dimension -> dimension != baseUnit)
                    .ifPresent(dimension -> {
                        throw new ApiException(
                                ErrorCode.UNIT_INCOMPATIBLE,
                                "La unidad '" + conversion.unit() + "' mide "
                                        + dimension.name() + " y el producto se guarda en "
                                        + baseUnit.label() + ".");
                    });
            if (!seen.add(conversion.unit())) {
                throw invalid("la unidad '" + conversion.unit() + "' está repetida");
            }
        }
        return List.copyOf(conversions);
    }

    private static ApiException invalid(String message) {
        return new ApiException(
                ErrorCode.VALIDATION_ERROR,
                "La petición contiene datos no válidos.",
                List.of(new ApiError.FieldViolation("conversions", message)));
    }
}
