package com.killa.cocina.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuración operativa del inventario.
 *
 * @param expiryWarningDays días de antelación con los que un lote entra en la alerta de caducidad
 */
@ConfigurationProperties(prefix = "cocina.inventory")
public record InventoryProperties(int expiryWarningDays) {

    public InventoryProperties {
        if (expiryWarningDays < 0) {
            throw new IllegalArgumentException("expiry-warning-days no puede ser negativo");
        }
    }
}
