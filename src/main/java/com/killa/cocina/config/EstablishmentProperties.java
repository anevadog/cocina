package com.killa.cocina.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Establecimiento activo del MVP (multi-tenencia resuelta por configuración).
 *
 * @param id identificador del establecimiento que usan todas las operaciones
 * @param name nombre visible; solo se usa al crear la fila semilla
 * @param timezone zona de negocio, usada únicamente en presentación e informes (PLA-06)
 * @param currency moneda del establecimiento; el MVP solo usa EUR
 */
@ConfigurationProperties(prefix = "cocina.establishment")
public record EstablishmentProperties(long id, String name, String timezone, String currency) {

    public EstablishmentProperties {
        name = blankTo(name, "Cocina");
        timezone = blankTo(timezone, "Europe/Madrid");
        currency = blankTo(currency, "EUR");
    }

    private static String blankTo(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }
}
