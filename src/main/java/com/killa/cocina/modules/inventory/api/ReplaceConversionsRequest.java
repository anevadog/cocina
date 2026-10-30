package com.killa.cocina.modules.inventory.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.List;

/**
 * Reemplazo del conjunto completo de conversiones de un producto (INV-02).
 *
 * <p>Enviar una lista vacía elimina todas las conversiones; la unidad base nunca se declara.</p>
 */
public record ReplaceConversionsRequest(
        @NotNull(message = "es obligatorio")
        List<@Valid ConversionRequest> conversions) {
}
