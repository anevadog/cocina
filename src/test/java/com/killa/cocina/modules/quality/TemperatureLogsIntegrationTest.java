package com.killa.cocina.modules.quality;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.killa.cocina.config.EstablishmentProperties;
import com.killa.cocina.modules.users.domain.Role;
import com.killa.cocina.modules.users.domain.UserRegistration;
import com.killa.cocina.modules.users.domain.UserService;
import com.killa.cocina.support.AbstractIntegrationTest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.dao.DataAccessException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/**
 * Temperaturas, alertas y protección de auditoría de punta a punta (CAL-02, CAL-06).
 *
 * <p>Comprueba contra PostgreSQL real que una lectura fuera de rango se guarda, que las alertas
 * solo muestran esas lecturas y que {@code audit_logs} no se puede modificar ni borrar.</p>
 */
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TemperatureLogsIntegrationTest extends AbstractIntegrationTest {

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
                        new UserRegistration("quality.cook", "Cocinera de calidad", PASSWORD, Role.COOK))
                .id();
        users.create(
                establishment.id(),
                null,
                new UserRegistration("quality.store", "Almacenera de calidad", PASSWORD, Role.STOREKEEPER));
        cookToken = loginToken("quality.cook");
        storekeeperToken = loginToken("quality.store");
    }

    @Test
    @DisplayName("Una lectura en rango se registra con su actor y queda auditada")
    void registraUnaLecturaEnRango() throws Exception {
        String body = register("EQUIPMENT", "Cámara en rango", "4", "2", "8", null, cookToken)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.withinRange").value(true))
                .andExpect(jsonPath("$.valueC").value(4.00))
                .andExpect(jsonPath("$.minC").value(2.00))
                .andExpect(jsonPath("$.maxC").value(8.00))
                .andExpect(jsonPath("$.actorId").value(cookId))
                .andReturn()
                .getResponse()
                .getContentAsString();

        long id = id(body, "$.id");
        mvc.perform(get("/api/v1/temperature-logs/" + id).header("Authorization", "Bearer " + cookToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.equipment").value("Cámara en rango"))
                .andExpect(jsonPath("$.withinRange").value(true));

        Map<String, Object> audit = jdbcTemplate.queryForMap("""
                select actor_id, after_json::text as after_json
                from audit_logs
                where action = 'TEMPERATURE_LOGGED' and entity_type = 'temperature_log' and entity_id = ?
                """, id);
        assertThat(((Number) audit.get("actor_id")).longValue()).isEqualTo(cookId);
        assertThat(audit.get("after_json").toString())
                .contains("\"withinRange\": true")
                .contains("\"equipment\": \"Cámara en rango\"")
                .contains("\"valueC\": \"4.00\"");
    }

    @Test
    @DisplayName("Una lectura fuera de rango se guarda y aparece en las alertas; la correcta no")
    void unaLecturaFueraDeRangoGeneraAlerta() throws Exception {
        Instant recordedAt = Instant.now().minusSeconds(120);
        String outside = register("PREPARATION", "Salsa fuera de rango", "12.5", "2", "8", recordedAt, cookToken)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.withinRange").value(false))
                .andReturn()
                .getResponse()
                .getContentAsString();
        String inside = register("PREPARATION", "Salsa en rango", "5", "2", "8", recordedAt.plusSeconds(1), cookToken)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.withinRange").value(true))
                .andReturn()
                .getResponse()
                .getContentAsString();

        long outsideId = id(outside, "$.id");
        long insideId = id(inside, "$.id");

        String alerts = mvc.perform(
                        get("/api/v1/quality/temperature-alerts").header("Authorization", "Bearer " + cookToken))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        List<Map<String, Object>> rows = JsonPath.read(alerts, "$");
        List<Long> ids =
                rows.stream().map(row -> ((Number) row.get("id")).longValue()).toList();
        assertThat(ids).contains(outsideId).doesNotContain(insideId);
        assertThat(rows)
                .filteredOn(row -> ((Number) row.get("id")).longValue() == outsideId)
                .singleElement()
                .satisfies(row -> {
                    assertThat(row.get("withinRange")).isEqualTo(false);
                    assertThat(row.get("equipment")).isEqualTo("Salsa fuera de rango");
                });
    }

    @Test
    @DisplayName("El rango, el equipo y la fecha de la lectura se validan")
    void validaLosDatosDeLaLectura() throws Exception {
        register("EQUIPMENT", "Cámara inválida", "5", "8", "8", null, cookToken)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        register("EQUIPMENT", "Cámara inválida", "5", "2", "8", Instant.now().plusSeconds(3600), cookToken)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.details[0].field").value("recordedAt"));

        register("EQUIPMENT", "A", "5", "2", "8", null, cookToken)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        register("EQUIPMENT", "Cámara decimal", "5.555", "2", "8", null, cookToken)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        register("EQUIPMENT", "Cámara fuera de rango numérico", "1000", "2", "8", null, cookToken)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    @DisplayName("Sin recordedAt se usa la hora del servidor y la lista se pagina")
    void usaLaHoraDelServidorYPagina() throws Exception {
        Instant base = Instant.now().minusSeconds(600);
        register("EQUIPMENT", "Paginación antigua", "4", "2", "8", base, cookToken)
                .andExpect(status().isCreated());
        register("EQUIPMENT", "Paginación reciente", "4", "2", "8", base.plusSeconds(60), cookToken)
                .andExpect(status().isCreated());
        register("EQUIPMENT", "Paginación intermedia", "4", "2", "8", base.plusSeconds(30), cookToken)
                .andExpect(status().isCreated());

        mvc.perform(get("/api/v1/temperature-logs?page=0&size=2").header("Authorization", "Bearer " + cookToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(2))
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.total").isNumber());

        String defaulted = register("PREPARATION", "Sin hora explícita", "4", "2", "8", null, cookToken)
                .andExpect(jsonPath("$.recordedAt").isNotEmpty())
                .andReturn()
                .getResponse()
                .getContentAsString();
        assertThat(JsonPath.<String>read(defaulted, "$.recordedAt")).isNotBlank();
    }

    @Test
    @DisplayName("Un almacenero solo consulta temperaturas; el cocinero las registra")
    void respetaLaMatrizDePermisos() throws Exception {
        register("EQUIPMENT", "Permisos temperatura", "4", "2", "8", null, storekeeperToken)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        mvc.perform(get("/api/v1/temperature-logs").header("Authorization", "Bearer " + storekeeperToken))
                .andExpect(status().isOk());
        mvc.perform(get("/api/v1/quality/temperature-alerts").header("Authorization", "Bearer " + storekeeperToken))
                .andExpect(status().isOk());

        register("EQUIPMENT", "Permisos temperatura", "4", "2", "8", null, cookToken)
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("Las alertas se limitan a las 100 lecturas fuera de rango más recientes")
    void lasAlertasSeLimitanA100() throws Exception {
        List<Object[]> batch = new ArrayList<>();
        Instant base = Instant.now().minusSeconds(3600);
        for (int i = 0; i < 105; i++) {
            batch.add(new Object[] {establishment.id(), cookId, java.sql.Timestamp.from(base.plusSeconds(i))});
        }
        jdbcTemplate.batchUpdate("""
                insert into temperature_logs
                    (establishment_id, kind, equipment, value_c, min_c, max_c, actor_id, recorded_at)
                values (?, 'EQUIPMENT', 'Límite de alertas', 20, 2, 8, ?, ?)
                """, batch);

        String body = mvc.perform(
                        get("/api/v1/quality/temperature-alerts").header("Authorization", "Bearer " + cookToken))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        List<Map<String, Object>> rows = JsonPath.read(body, "$");
        assertThat(rows).hasSize(100);
        assertThat(rows).allSatisfy(row -> assertThat(row.get("withinRange")).isEqualTo(false));
    }

    @Test
    @DisplayName("Las lecturas no existen fuera del establecimiento del token")
    void unaLecturaInexistenteDevuelve404() throws Exception {
        mvc.perform(get("/api/v1/temperature-logs/999999").header("Authorization", "Bearer " + cookToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    @DisplayName("audit_logs es append-only también por SQL directo")
    void laAuditoriaEsAppendOnly() throws Exception {
        long id = id(
                register("EQUIPMENT", "Auditoría inmutable", "4", "2", "8", null, cookToken)
                        .andReturn()
                        .getResponse()
                        .getContentAsString(),
                "$.id");
        Long auditId = jdbcTemplate.queryForObject("""
                select id from audit_logs
                where action = 'TEMPERATURE_LOGGED' and entity_type = 'temperature_log' and entity_id = ?
                order by id desc limit 1
                """, Long.class, id);
        assertThat(auditId).isNotNull();

        assertThatThrownBy(() -> jdbcTemplate.update("update audit_logs set action = 'ALTERADO' where id = ?", auditId))
                .isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> jdbcTemplate.update("delete from audit_logs where id = ?", auditId))
                .isInstanceOf(DataAccessException.class);

        assertThat(jdbcTemplate.queryForObject("select action from audit_logs where id = ?", String.class, auditId))
                .isEqualTo("TEMPERATURE_LOGGED");
    }

    private ResultActions register(
            String kind, String equipment, String value, String min, String max, Instant recordedAt, String token)
            throws Exception {
        String body = """
                {"kind":"%s","equipment":"%s","valueC":%s,"minC":%s,"maxC":%s%s}
                """.formatted(
                kind, equipment, value, min, max, recordedAt == null ? "" : ",\"recordedAt\":\"" + recordedAt + "\"");
        return mvc.perform(post("/api/v1/temperature-logs")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
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
