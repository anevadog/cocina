package com.killa.cocina.modules.quality;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.killa.cocina.config.EstablishmentProperties;
import com.killa.cocina.modules.users.domain.Role;
import com.killa.cocina.modules.users.domain.UserRegistration;
import com.killa.cocina.modules.users.domain.UserService;
import com.killa.cocina.support.AbstractIntegrationTest;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/**
 * Incidencias de punta a punta (CAL-05).
 *
 * <p>Comprueba contra PostgreSQL real el ciclo OPEN → IN_PROGRESS → CLOSED, el bloqueo de la
 * reapertura y de los cambios tras el cierre, la resolución obligatoria al cerrar, la auditoría de
 * apertura y cambios y la matriz de permisos.</p>
 */
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class IncidentsIntegrationTest extends AbstractIntegrationTest {

    private static final String PASSWORD = "contrasena-segura-2026";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private UserService users;

    @Autowired
    private EstablishmentProperties establishment;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private long cookId;

    private String cookToken;

    private String storekeeperToken;

    @BeforeAll
    void prepararDatos() throws Exception {
        cookId = users.create(
                        establishment.id(),
                        null,
                        new UserRegistration("incident.cook", "Cocinera de incidencias", PASSWORD, Role.COOK))
                .id();
        users.create(
                establishment.id(),
                null,
                new UserRegistration("incident.store", "Almacenera de incidencias", PASSWORD, Role.STOREKEEPER));
        cookToken = loginToken("incident.cook");
        storekeeperToken = loginToken("incident.store");
    }

    @Test
    @DisplayName("La incidencia se abre en OPEN y se audita con su actor")
    void abreLaIncidencia() throws Exception {
        String body = createIncident("Cámara con hielo acumulado en el fondo", "HIGH", cookToken)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.description").value("Cámara con hielo acumulado en el fondo"))
                .andExpect(jsonPath("$.severity").value("HIGH"))
                .andExpect(jsonPath("$.status").value("OPEN"))
                .andExpect(jsonPath("$.resolution").value(nullValue()))
                .andExpect(jsonPath("$.openedBy").value(cookId))
                .andExpect(jsonPath("$.openedAt").isNotEmpty())
                .andExpect(jsonPath("$.updatedAt").isNotEmpty())
                .andExpect(jsonPath("$.closedBy").value(nullValue()))
                .andExpect(jsonPath("$.closedAt").value(nullValue()))
                .andReturn()
                .getResponse()
                .getContentAsString();
        long incidentId = id(body, "$.id");

        Map<String, Object> audit = jdbcTemplate.queryForMap("""
                select actor_id, before_json::text as before_json, after_json::text as after_json
                from audit_logs
                where action = 'INCIDENT_OPENED' and entity_type = 'incident' and entity_id = ?
                """, incidentId);
        assertThat(((Number) audit.get("actor_id")).longValue()).isEqualTo(cookId);
        assertThat(audit.get("before_json")).isNull();
        assertThat(audit.get("after_json").toString())
                .contains("\"incidentId\": " + incidentId)
                .contains("\"status\": \"OPEN\"")
                .contains("\"severity\": \"HIGH\"");
    }

    @Test
    @DisplayName("OPEN → IN_PROGRESS → CLOSED exige resolución al cerrar y lo audita")
    void recorreElCicloDeVida() throws Exception {
        long incidentId = id(
                createIncident("Fuga de agua bajo la cámara", "CRITICAL", cookToken)
                        .andExpect(status().isCreated())
                        .andReturn()
                        .getResponse()
                        .getContentAsString(),
                "$.id");

        changeStatus(incidentId, "IN_PROGRESS", null, cookToken)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.resolution").value(nullValue()));

        changeStatus(incidentId, "CLOSED", "Se reparó la junta y se secó la zona", cookToken)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CLOSED"))
                .andExpect(jsonPath("$.resolution").value("Se reparó la junta y se secó la zona"))
                .andExpect(jsonPath("$.closedBy").value(cookId))
                .andExpect(jsonPath("$.closedAt").isNotEmpty());

        assertThat(jdbcTemplate.queryForObject(
                        "select count(*) from incidents where id = ? and resolution is not null"
                                + " and closed_by = ? and closed_at is not null",
                        Integer.class,
                        incidentId,
                        cookId))
                .isEqualTo(1);

        List<Map<String, Object>> audits = jdbcTemplate.queryForList("""
                select actor_id, before_json::text as before_json, after_json::text as after_json
                from audit_logs
                where action = 'INCIDENT_STATUS_CHANGED' and entity_type = 'incident' and entity_id = ?
                order by id
                """, incidentId);
        assertThat(audits).hasSize(2);
        assertThat(((Number) audits.get(0).get("actor_id")).longValue()).isEqualTo(cookId);
        assertThat(audits.get(0).get("before_json").toString()).contains("\"status\": \"OPEN\"");
        assertThat(audits.get(0).get("after_json").toString()).contains("\"status\": \"IN_PROGRESS\"");
        assertThat(audits.get(1).get("before_json").toString()).contains("\"status\": \"IN_PROGRESS\"");
        assertThat(audits.get(1).get("after_json").toString())
                .contains("\"status\": \"CLOSED\"")
                .contains("\"resolution\": \"Se reparó la junta y se secó la zona\"");
    }

    @Test
    @DisplayName("Cerrar sin resolución, con resolución fuera de cierre o reabrir devuelve 400")
    void validaLasTransiciones() throws Exception {
        long incidentId = id(
                createIncident("Envasado sin etiqueta visible", "MEDIUM", cookToken)
                        .andExpect(status().isCreated())
                        .andReturn()
                        .getResponse()
                        .getContentAsString(),
                "$.id");

        changeStatus(incidentId, "CLOSED", null, cookToken)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.details[0].field").value("resolution"));

        changeStatus(incidentId, "IN_PROGRESS", "Resolución prematura", cookToken)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.details[0].field").value("resolution"));

        changeStatus(incidentId, "OPEN", null, cookToken)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.details[0].field").value("status"));

        assertThat(incidentStatus(incidentId)).isEqualTo("OPEN");
        assertThat(jdbcTemplate.queryForObject(
                        "select count(*) from audit_logs where action = 'INCIDENT_STATUS_CHANGED'"
                                + " and entity_type = 'incident' and entity_id = ?",
                        Integer.class,
                        incidentId))
                .isZero();
    }

    @Test
    @DisplayName("Una incidencia cerrada no admite más cambios")
    void unaCerradaNoCambia() throws Exception {
        long incidentId = id(
                createIncident("Contenedor sin tapa en zona sucia", "HIGH", cookToken)
                        .andExpect(status().isCreated())
                        .andReturn()
                        .getResponse()
                        .getContentAsString(),
                "$.id");

        changeStatus(incidentId, "CLOSED", "Se sustituyó el contenedor", cookToken)
                .andExpect(status().isOk());

        changeStatus(incidentId, "IN_PROGRESS", null, cookToken)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("RESOURCE_IN_USE"));

        changeStatus(incidentId, "CLOSED", "Otro cierre distinto", cookToken)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("RESOURCE_IN_USE"));

        assertThat(incidentStatus(incidentId)).isEqualTo("CLOSED");
        assertThat(jdbcTemplate.queryForObject(
                        "select resolution from incidents where id = ?", String.class, incidentId))
                .isEqualTo("Se sustituyó el contenedor");
    }

    @Test
    @DisplayName("La petición se valida: descripción, severidad y estado")
    void validaLaPeticion() throws Exception {
        createIncident("corta", "LOW", cookToken)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.details[0].field").value("description"));

        mvc.perform(post("/api/v1/incidents")
                        .header("Authorization", "Bearer " + cookToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"description":"Descripción sin severidad"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.details[0].field").value("severity"));

        long incidentId = id(
                createIncident("Descripción válida para cambiar estado", "LOW", cookToken)
                        .andExpect(status().isCreated())
                        .andReturn()
                        .getResponse()
                        .getContentAsString(),
                "$.id");
        mvc.perform(patch("/api/v1/incidents/" + incidentId)
                        .header("Authorization", "Bearer " + cookToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details[0].field").value("status"));
    }

    @Test
    @DisplayName("Se consulta por id y se pagina con las más recientes primero")
    void consultaYPagina() throws Exception {
        long first = id(
                createIncident("Primera incidencia paginada", "LOW", cookToken)
                        .andExpect(status().isCreated())
                        .andReturn()
                        .getResponse()
                        .getContentAsString(),
                "$.id");
        long second = id(
                createIncident("Segunda incidencia paginada", "HIGH", cookToken)
                        .andExpect(status().isCreated())
                        .andReturn()
                        .getResponse()
                        .getContentAsString(),
                "$.id");

        mvc.perform(get("/api/v1/incidents/" + first).header("Authorization", "Bearer " + cookToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(first))
                .andExpect(jsonPath("$.severity").value("LOW"));

        String page = mvc.perform(get("/api/v1/incidents?page=0&size=1").header("Authorization", "Bearer " + cookToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(1))
                .andExpect(jsonPath("$.content.length()").value(1))
                .andReturn()
                .getResponse()
                .getContentAsString();
        List<Map<String, Object>> rows = JsonPath.read(page, "$.content");
        assertThat(((Number) rows.get(0).get("id")).longValue()).isEqualTo(second);

        mvc.perform(get("/api/v1/incidents/999999").header("Authorization", "Bearer " + cookToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    @DisplayName("Solo el cocinero registra y cambia incidencias; el almacenero las consulta")
    void respetaLaMatrizDePermisos() throws Exception {
        createIncident("Incidencia sin permiso de escritura", "LOW", storekeeperToken)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        long incidentId = id(
                createIncident("Incidencia con permiso de escritura", "LOW", cookToken)
                        .andExpect(status().isCreated())
                        .andReturn()
                        .getResponse()
                        .getContentAsString(),
                "$.id");

        changeStatus(incidentId, "IN_PROGRESS", null, storekeeperToken)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        mvc.perform(get("/api/v1/incidents").header("Authorization", "Bearer " + storekeeperToken))
                .andExpect(status().isOk());
        mvc.perform(get("/api/v1/incidents/" + incidentId).header("Authorization", "Bearer " + storekeeperToken))
                .andExpect(status().isOk());
    }

    private ResultActions createIncident(String description, String severity, String token) throws Exception {
        String body = """
                {"description":"%s","severity":"%s"}
                """.formatted(description, severity);
        return mvc.perform(post("/api/v1/incidents")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    private ResultActions changeStatus(long incidentId, String status, String resolution, String token)
            throws Exception {
        String body = resolution == null ? """
                        {"status":"%s"}
                        """.formatted(status) : """
                        {"status":"%s","resolution":"%s"}
                        """.formatted(status, resolution);
        return mvc.perform(patch("/api/v1/incidents/" + incidentId)
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    private String incidentStatus(long incidentId) {
        return jdbcTemplate.queryForObject("select status from incidents where id = ?", String.class, incidentId);
    }

    private String loginToken(String username) throws Exception {
        String body = mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"%s","password":"%s"}
                                """.formatted(username, PASSWORD)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return JsonPath.read(body, "$.accessToken");
    }

    private static long id(String body, String path) {
        return ((Number) JsonPath.read(body, path)).longValue();
    }
}
