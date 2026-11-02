package com.killa.cocina.modules.quality.domain;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Datos de entrada de una lectura de temperatura (CAL-02).
 *
 * <p>{@code recordedAt} es opcional: si falta, el servicio usa el reloj del servidor. El cliente no
 * puede declarar una lectura futura.</p>
 */
public record TemperatureLogRegistration(
        TemperatureKind kind,
        String equipment,
        BigDecimal valueC,
        BigDecimal minC,
        BigDecimal maxC,
        Instant recordedAt) {

    /** Longitud mínima del nombre del equipo o preparación. */
    public static final int MIN_EQUIPMENT_LENGTH = 2;

    /** Longitud máxima del nombre del equipo o preparación (varchar(80)). */
    public static final int MAX_EQUIPMENT_LENGTH = 80;

    public TemperatureLogRegistration {
        if (kind == null) {
            throw TemperatureLog.invalid("kind", "es obligatorio");
        }
        equipment = normalizeEquipment(equipment);
        valueC = TemperatureLog.normalize("valueC", valueC);
        minC = TemperatureLog.normalize("minC", minC);
        maxC = TemperatureLog.normalize("maxC", maxC);
        if (minC.compareTo(maxC) >= 0) {
            throw TemperatureLog.invalid("minC", "debe ser menor que maxC");
        }
    }

    static String normalizeEquipment(String equipment) {
        if (equipment == null || equipment.isBlank()) {
            throw TemperatureLog.invalid("equipment", "es obligatorio");
        }
        String normalized = equipment.trim().replaceAll("\\s+", " ");
        if (normalized.length() < MIN_EQUIPMENT_LENGTH || normalized.length() > MAX_EQUIPMENT_LENGTH) {
            throw TemperatureLog.invalid(
                    "equipment",
                    "debe tener entre " + MIN_EQUIPMENT_LENGTH + " y " + MAX_EQUIPMENT_LENGTH + " caracteres");
        }
        return normalized;
    }
}
