package com.killa.cocina.modules.quality.api;

import com.killa.cocina.modules.quality.domain.TraceabilityService;
import com.killa.cocina.shared.error.ApiException;
import com.killa.cocina.shared.error.ErrorCode;
import com.killa.cocina.shared.security.CurrentActor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Trazabilidad de lotes (CAL-01).
 *
 * <p>Dado un lote, devuelve qué preparaciones lo consumieron y cuándo. El lote no tiene que tener
 * saldo: el histórico del kardex es la fuente de verdad.</p>
 */
@RestController
@RequestMapping("/api/v1/stock-lots")
public class TraceabilityController {

    private final TraceabilityService traceability;

    private final CurrentActor currentActor;

    public TraceabilityController(TraceabilityService traceability, CurrentActor currentActor) {
        this.traceability = traceability;
        this.currentActor = currentActor;
    }

    @GetMapping("/{lotId}/traceability")
    @PreAuthorize("hasAuthority('quality:read')")
    public LotTraceabilityResponse trace(@PathVariable long lotId) {
        CurrentActor.Actor actor = requireActor();
        return LotTraceabilityResponse.from(traceability.traceLot(actor.establishmentId(), lotId));
    }

    private CurrentActor.Actor requireActor() {
        return currentActor
                .find()
                .orElseThrow(() -> new ApiException(
                        ErrorCode.UNAUTHORIZED, "Necesitas autenticarte para acceder a este recurso."));
    }
}
