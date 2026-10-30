package com.killa.cocina.shared.health;

import org.flywaydb.core.Flyway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Comprueba que no quedan migraciones pendientes (PLA-04, PLA-05).
 *
 * <p>La aplicación aplica las migraciones al arrancar; si aparecen pendientes es que el esquema no
 * corresponde con el binario desplegado.</p>
 */
@Component
public class MigrationReadinessIndicator implements ReadinessIndicator {

    private static final Logger log = LoggerFactory.getLogger(MigrationReadinessIndicator.class);

    private final Flyway flyway;

    public MigrationReadinessIndicator(Flyway flyway) {
        this.flyway = flyway;
    }

    @Override
    public String name() {
        return "migraciones";
    }

    @Override
    public ReadinessStatus status() {
        try {
            return flyway.info().pending().length == 0 ? ReadinessStatus.UP : ReadinessStatus.DOWN;
        } catch (RuntimeException exception) {
            log.warn("La comprobación del estado de las migraciones ha fallado: {}", exception.getMessage());
            return ReadinessStatus.DOWN;
        }
    }
}
