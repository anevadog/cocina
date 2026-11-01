package com.killa.cocina.modules.inventory.domain;

import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Unidad base en la que se persiste el stock (INV-03).
 *
 * <p>No hay conversiones entre dimensiones: un producto de peso nunca se mide en mililitros. El
 * factor de cada unidad de compra se guarda por producto en {@link UnitConversion}.</p>
 */
public enum BaseUnit {

    /** Gramos. */
    G("g"),

    /** Mililitros. */
    ML("ml"),

    /** Unidades. */
    UNIT("ud");

    /**
     * Unidades conocidas y su dimensión. Sirve para rechazar una conversión incoherente (declarar
     * litros en un producto de peso) sin cerrar la puerta a unidades de compra propias, como
     * «caja» o «docena», cuya dimensión se asume la del producto.
     */
    private static final Map<String, BaseUnit> KNOWN_UNITS = Map.ofEntries(
            Map.entry("g", G),
            Map.entry("kg", G),
            Map.entry("mg", G),
            Map.entry("oz", G),
            Map.entry("lb", G),
            Map.entry("ml", ML),
            Map.entry("l", ML),
            Map.entry("cl", ML),
            Map.entry("dl", ML),
            Map.entry("ud", UNIT),
            Map.entry("u", UNIT),
            Map.entry("unidad", UNIT));

    private final String label;

    BaseUnit(String label) {
        this.label = label;
    }

    /** Etiqueta que se acepta como unidad base al registrar un movimiento. */
    public String label() {
        return label;
    }

    /** Reconoce la etiqueta de la unidad base, sin distinguir mayúsculas. */
    public static Optional<BaseUnit> fromLabel(String label) {
        if (label == null) {
            return Optional.empty();
        }
        String normalized = label.trim().toLowerCase(Locale.ROOT);
        for (BaseUnit unit : values()) {
            if (unit.label.equals(normalized)
                    || unit.name().toLowerCase(Locale.ROOT).equals(normalized)) {
                return Optional.of(unit);
            }
        }
        return Optional.empty();
    }

    /** Dimensión de una unidad conocida; vacío para etiquetas propias del producto. */
    public static Optional<BaseUnit> dimensionOf(String label) {
        if (label == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(KNOWN_UNITS.get(label.trim().toLowerCase(Locale.ROOT)));
    }
}
