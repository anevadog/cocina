package com.killa.cocina.modules.quality.api;

import com.killa.cocina.modules.quality.domain.TemperatureService;
import com.killa.cocina.shared.error.ApiException;
import com.killa.cocina.shared.error.ErrorCode;
import com.killa.cocina.shared.security.CurrentActor;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Consultas agregadas de calidad (CAL-02). */
@RestController
@RequestMapping("/api/v1/quality")
public class QualityAlertController {

    private final TemperatureService temperatures;

    private final CurrentActor currentActor;

    public QualityAlertController(TemperatureService temperatures, CurrentActor currentActor) {
        this.temperatures = temperatures;
        this.currentActor = currentActor;
    }

    /** Lecturas fuera de rango más recientes, como máximo las 100 últimas. */
    @GetMapping("/temperature-alerts")
    @PreAuthorize("hasAuthority('quality:read')")
    public List<TemperatureLogResponse> temperatureAlerts() {
        CurrentActor.Actor actor = requireActor();
        return temperatures.alerts(actor.establishmentId()).stream()
                .map(TemperatureLogResponse::from)
                .toList();
    }

    private CurrentActor.Actor requireActor() {
        return currentActor
                .find()
                .orElseThrow(() -> new ApiException(
                        ErrorCode.UNAUTHORIZED, "Necesitas autenticarte para acceder a este recurso."));
    }
}
