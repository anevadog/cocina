package com.killa.cocina.modules.quality.api;

import com.killa.cocina.modules.quality.domain.IncidentService;
import com.killa.cocina.shared.error.ApiException;
import com.killa.cocina.shared.error.ErrorCode;
import com.killa.cocina.shared.security.CurrentActor;
import com.killa.cocina.shared.web.PageResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Incidencias de calidad (CAL-05).
 *
 * <p>La descripción y la severidad no se editan; solo el estado y, al cerrar, la resolución. Una
 * incidencia cerrada no se reabre (ADR-040).</p>
 */
@RestController
@RequestMapping("/api/v1/incidents")
@Validated
public class IncidentController {

    private final IncidentService incidents;

    private final CurrentActor currentActor;

    public IncidentController(IncidentService incidents, CurrentActor currentActor) {
        this.incidents = incidents;
        this.currentActor = currentActor;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('quality:read')")
    public PageResponse<IncidentResponse> list(
            @RequestParam(defaultValue = "0") @Min(value = 0, message = "no puede ser negativo") int page,
            @RequestParam(defaultValue = "20")
                    @Min(value = 1, message = "debe ser al menos 1")
                    @Max(value = 100, message = "no puede superar 100")
                    int size) {
        CurrentActor.Actor actor = requireActor();
        return PageResponse.from(incidents.list(actor.establishmentId(), page, size), IncidentResponse::from);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('quality:write')")
    public IncidentResponse create(@Valid @RequestBody CreateIncidentRequest request) {
        CurrentActor.Actor actor = requireActor();
        return IncidentResponse.from(
                incidents.register(actor.establishmentId(), actor.userId(), request.toRegistration()));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('quality:read')")
    public IncidentResponse find(@PathVariable long id) {
        CurrentActor.Actor actor = requireActor();
        return IncidentResponse.from(incidents.find(actor.establishmentId(), id));
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasAuthority('quality:write')")
    public IncidentResponse changeStatus(@PathVariable long id, @Valid @RequestBody UpdateIncidentRequest request) {
        CurrentActor.Actor actor = requireActor();
        return IncidentResponse.from(incidents.changeStatus(
                actor.establishmentId(), actor.userId(), id, request.status(), request.resolution()));
    }

    private CurrentActor.Actor requireActor() {
        return currentActor
                .find()
                .orElseThrow(() -> new ApiException(
                        ErrorCode.UNAUTHORIZED, "Necesitas autenticarte para acceder a este recurso."));
    }
}
