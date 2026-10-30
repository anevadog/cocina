package com.killa.cocina.shared.health;

import java.time.Clock;
import java.time.Instant;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Sonda de vida del proceso (PLA-04). Endpoint público y sin autenticación.
 *
 * <p>Responde mientras la aplicación esté en marcha; no comprueba dependencias externas. La
 * comprobación de la base de datos y de las migraciones corresponde a {@code /ready}.</p>
 */
@RestController
public class HealthController {

    private final Clock clock;

    private final String serviceName;

    public HealthController(Clock clock, @Value("${spring.application.name:cocina-api}") String serviceName) {
        this.clock = clock;
        this.serviceName = serviceName;
    }

    @GetMapping("/health")
    public HealthResponse health() {
        return new HealthResponse("UP", serviceName, Instant.now(clock));
    }
}
