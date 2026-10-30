package com.killa.cocina.shared.health;

import static org.assertj.core.api.Assertions.assertThat;

import com.killa.cocina.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/**
 * Contrato de {@code /ready} contra PostgreSQL real (PLA-04).
 */
class ReadinessIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private ReadinessController readinessController;

    @Test
    void conBaseDeDatosYMigracionesAlDiaElServicioEstaListo() {
        ResponseEntity<ReadinessResponse> response = readinessController.ready();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().status()).isEqualTo("READY");
        assertThat(response.getBody().checks())
                .extracting(ReadinessResponse.Check::name)
                .containsExactly("migraciones", "postgres");
        assertThat(response.getBody().checks())
                .extracting(ReadinessResponse.Check::status)
                .containsOnly("UP");
    }
}
