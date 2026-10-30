package com.killa.cocina.shared.health;

import java.sql.Connection;
import java.sql.SQLException;
import javax.sql.DataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Comprueba que la base de datos responde (PLA-04).
 *
 * <p>Abre una conexión del pool y valida la sesión; si falla, {@code /ready} devuelve 503 para que
 * el orquestador deje de enviar tráfico.</p>
 */
@Component
public class DatabaseReadinessIndicator implements ReadinessIndicator {

    private static final Logger log = LoggerFactory.getLogger(DatabaseReadinessIndicator.class);

    private final DataSource dataSource;

    public DatabaseReadinessIndicator(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public String name() {
        return "postgres";
    }

    @Override
    public ReadinessStatus status() {
        try (Connection connection = dataSource.getConnection()) {
            return connection.isValid(1) ? ReadinessStatus.UP : ReadinessStatus.DOWN;
        } catch (SQLException exception) {
            log.warn("La comprobación de preparación de PostgreSQL ha fallado: {}", exception.getMessage());
            return ReadinessStatus.DOWN;
        }
    }
}
