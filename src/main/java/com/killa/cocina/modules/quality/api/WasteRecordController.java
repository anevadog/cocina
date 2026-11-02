package com.killa.cocina.modules.quality.api;

import com.killa.cocina.modules.quality.domain.WasteService;
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
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Mermas de calidad (CAL-03, CAL-04).
 *
 * <p>El alta descuenta stock de forma atómica y valora la merma con el PMP vigente; el kardex deja un
 * movimiento {@code WASTE_OUT} por cada lote consumido.</p>
 */
@RestController
@RequestMapping("/api/v1/waste-records")
@Validated
public class WasteRecordController {

    private final WasteService wastes;

    private final CurrentActor currentActor;

    public WasteRecordController(WasteService wastes, CurrentActor currentActor) {
        this.wastes = wastes;
        this.currentActor = currentActor;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('quality:read')")
    public PageResponse<WasteRecordResponse> list(
            @RequestParam(defaultValue = "0") @Min(value = 0, message = "no puede ser negativo") int page,
            @RequestParam(defaultValue = "20")
                    @Min(value = 1, message = "debe ser al menos 1")
                    @Max(value = 100, message = "no puede superar 100")
                    int size) {
        CurrentActor.Actor actor = requireActor();
        return PageResponse.from(wastes.list(actor.establishmentId(), page, size), WasteRecordResponse::from);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('quality:write')")
    public WasteRecordResponse create(@Valid @RequestBody CreateWasteRecordRequest request) {
        CurrentActor.Actor actor = requireActor();
        return WasteRecordResponse.from(
                wastes.register(actor.establishmentId(), actor.userId(), request.toRegistration()));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('quality:read')")
    public WasteRecordResponse find(@PathVariable long id) {
        CurrentActor.Actor actor = requireActor();
        return WasteRecordResponse.from(wastes.find(actor.establishmentId(), id));
    }

    private CurrentActor.Actor requireActor() {
        return currentActor
                .find()
                .orElseThrow(() -> new ApiException(
                        ErrorCode.UNAUTHORIZED, "Necesitas autenticarte para acceder a este recurso."));
    }
}
