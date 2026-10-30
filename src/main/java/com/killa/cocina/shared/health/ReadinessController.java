package com.killa.cocina.shared.health;

import java.util.Comparator;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Sonda de preparación (PLA-04). Endpoint público y sin autenticación.
 *
 * <p>Responde 200 cuando todas las comprobaciones están {@code UP} y 503 cuando alguna está
 * {@code DOWN}, para que el orquestador deje de enviar tráfico.</p>
 */
@RestController
public class ReadinessController {

    private final List<ReadinessIndicator> indicators;

    public ReadinessController(List<ReadinessIndicator> indicators) {
        this.indicators = List.copyOf(indicators);
    }

    @GetMapping("/ready")
    public ResponseEntity<ReadinessResponse> ready() {
        List<ReadinessResponse.Check> checks = indicators.stream()
                .map(indicator -> new ReadinessResponse.Check(indicator.name(), indicator.status().name()))
                .sorted(Comparator.comparing(ReadinessResponse.Check::name))
                .toList();
        boolean ready = checks.stream().noneMatch(check -> ReadinessStatus.DOWN.name().equals(check.status()));
        HttpStatus status = ready ? HttpStatus.OK : HttpStatus.SERVICE_UNAVAILABLE;
        return ResponseEntity.status(status)
                .body(new ReadinessResponse(ready ? "READY" : "NOT_READY", checks));
    }
}
