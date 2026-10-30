package com.killa.cocina.shared.health;

/**
 * Puerto de comprobación de preparación.
 *
 * <p>Cada dependencia externa (base de datos, migraciones) implementa esta interfaz y se registra
 * como bean; {@code /ready} las agrega sin conocer los detalles de infraestructura.</p>
 */
public interface ReadinessIndicator {

    /** Nombre estable de la comprobación, por ejemplo {@code postgres}. */
    String name();

    ReadinessStatus status();
}
