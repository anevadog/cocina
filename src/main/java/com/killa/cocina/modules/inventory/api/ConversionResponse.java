package com.killa.cocina.modules.inventory.api;

import com.killa.cocina.modules.inventory.domain.UnitConversion;
import java.math.BigDecimal;

/** Conversión de unidad expuesta por la API. */
public record ConversionResponse(String unit, BigDecimal factorToBase) {

    public static ConversionResponse from(UnitConversion conversion) {
        return new ConversionResponse(conversion.unit(), conversion.factorToBase());
    }
}
