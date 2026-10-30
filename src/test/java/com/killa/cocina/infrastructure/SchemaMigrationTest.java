package com.killa.cocina.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import com.killa.cocina.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Verifica que Flyway aplica el esquema base sobre PostgreSQL real (PLA-05).
 */
class SchemaMigrationTest extends AbstractIntegrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void aplicaLaMigracionBaseline() {
        Integer versiones = jdbcTemplate.queryForObject(
                "select count(*) from flyway_schema_history where success and version = '1'", Integer.class);

        assertThat(versiones).isEqualTo(1);
    }

    @Test
    void creaLaTablaDeEstablecimientosConSusColumnas() {
        Integer columnas = jdbcTemplate.queryForObject("""
                select count(*) from information_schema.columns
                where table_name = 'establishments'
                  and column_name in ('id', 'name', 'timezone', 'currency', 'active', 'created_at', 'updated_at')
                """, Integer.class);

        assertThat(columnas).isEqualTo(7);
    }

    @Test
    void elEstablecimientoUsaSecuenciaYZonaHorariaPorDefecto() {
        jdbcTemplate.update("insert into establishments (name) values ('Cocina de pruebas')");

        String timezone = jdbcTemplate.queryForObject(
                "select timezone from establishments where name = 'Cocina de pruebas'", String.class);

        assertThat(timezone).isEqualTo("Europe/Madrid");
    }
}
