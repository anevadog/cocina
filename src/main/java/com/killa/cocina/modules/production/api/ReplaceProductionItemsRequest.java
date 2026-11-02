package com.killa.cocina.modules.production.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * Sustitución del conjunto completo de ítems de un plan pendiente (PRD-01).
 *
 * <p>Una lista vacía deja el plan sin recetas; las necesidades agregadas quedarán vacías.</p>
 */
public record ReplaceProductionItemsRequest(
        @NotNull(message = "es obligatorio") @Size(max = 200, message = "no puede superar 200 elementos")
        List<@Valid ProductionItemRequest> items) {}
