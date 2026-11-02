package com.killa.cocina.modules.quality.api;

import com.killa.cocina.modules.quality.domain.TemperatureService;
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
 * Lecturas de temperatura (CAL-02).
 *
 * <p>Una lectura fuera de rango se guarda igual que una correcta; la diferencia se ve en
 * {@code withinRange} y en las alertas de calidad.</p>
 */
@RestController
@RequestMapping("/api/v1/temperature-logs")
@Validated
public class TemperatureLogController {

    private final TemperatureService temperatures;

    private final CurrentActor currentActor;

    public TemperatureLogController(TemperatureService temperatures, CurrentActor currentActor) {
        this.temperatures = temperatures;
        this.currentActor = currentActor;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('quality:read')")
    public PageResponse<TemperatureLogResponse> list(
            @RequestParam(defaultValue = "0") @Min(value = 0, message = "no puede ser negativo") int page,
            @RequestParam(defaultValue = "20")
                    @Min(value = 1, message = "debe ser al menos 1")
                    @Max(value = 100, message = "no puede superar 100")
                    int size) {
        CurrentActor.Actor actor = requireActor();
        return PageResponse.from(temperatures.list(actor.establishmentId(), page, size), TemperatureLogResponse::from);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('quality:write')")
    public TemperatureLogResponse create(@Valid @RequestBody CreateTemperatureLogRequest request) {
        CurrentActor.Actor actor = requireActor();
        return TemperatureLogResponse.from(
                temperatures.register(actor.establishmentId(), actor.userId(), request.toRegistration()));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('quality:read')")
    public TemperatureLogResponse find(@PathVariable long id) {
        CurrentActor.Actor actor = requireActor();
        return TemperatureLogResponse.from(temperatures.find(actor.establishmentId(), id));
    }

    private CurrentActor.Actor requireActor() {
        return currentActor
                .find()
                .orElseThrow(() -> new ApiException(
                        ErrorCode.UNAUTHORIZED, "Necesitas autenticarte para acceder a este recurso."));
    }
}
