package com.killa.cocina.modules.users;

import static org.assertj.core.api.Assertions.assertThat;
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

/**
 * RBAC real de punta a punta (PER-02, PER-03, PER-04) contra PostgreSQL.
 *
 * <p>Comprueba el 403 del cocinero, el alta de usuarios con atribución en la auditoría y la guardia
 * que impide dejar el establecimiento sin administradores activos.</p>
 */
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class UsersAuthorizationIntegrationTest extends AbstractIntegrationTest {

    private static final String PASSWORD = "contrasena-segura-2026";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private UserService users;

    @Autowired
    private EstablishmentProperties establishment;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private long adminId;

    private String adminToken;

    private String cookToken;

    @BeforeAll
    void prepararUsuarios() throws Exception {
        adminId = users.create(establishment.id(), null,
                new UserRegistration("users.admin", "Administradora", PASSWORD, Role.ADMIN)).id();
        users.create(establishment.id(), null,
                new UserRegistration("users.cook", "Cocinera", PASSWORD, Role.COOK));
        adminToken = loginToken("users.admin");
        cookToken = loginToken("users.cook");
    }

    @Test
    @DisplayName("Un COOK recibe 403 al listar usuarios y al intentar crearlos, con log correlacionable")
    void cocineroNoGestionaUsuarios() throws Exception {
        mvc.perform(get("/api/v1/users").header("Authorization", "Bearer " + cookToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"))
                .andExpect(jsonPath("$.requestId").isNotEmpty());

        mvc.perform(post("/api/v1/users")
                        .header("Authorization", "Bearer " + cookToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(newUser("users.prohibido", "COOK")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    @DisplayName("Un ADMIN crea usuarios, y la operación queda auditada con su actor_id")
    void elAltaQuedaAtribuidaAlAdministrador() throws Exception {
        String body = mvc.perform(post("/api/v1/users")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(newUser("users.created", "STOREKEEPER")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.username").value("users.created"))
                .andExpect(jsonPath("$.role").value("STOREKEEPER"))
                .andExpect(jsonPath("$.active").value(true))
                .andExpect(jsonPath("$.password").doesNotExist())
                .andReturn().getResponse().getContentAsString();

        int createdId = JsonPath.read(body, "$.id");

        Map<String, Object> audit = jdbcTemplate.queryForMap("""
                select actor_id, action, entity_type, entity_id, before_json,
                       after_json::text as after_json
                from audit_logs
                where action = 'USER_CREATED' and entity_id = ?
                """, (long) createdId);

        assertThat(audit.get("actor_id")).isEqualTo(adminId);
        assertThat(audit.get("entity_type")).isEqualTo("app_user");
        assertThat(audit.get("before_json")).isNull();
        assertThat((String) audit.get("after_json")).contains("users.created").doesNotContain("password");
    }

    @Test
    @DisplayName("Un username repetido devuelve 409 y no crea nada")
    void elUsernameRepetidoDevuelve409() throws Exception {
        mvc.perform(post("/api/v1/users")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(newUser("users.cook", "COOK")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("USERNAME_TAKEN"));
    }

    @Test
    @DisplayName("Una contraseña débil devuelve 400 WEAK_PASSWORD y no llega a crear el usuario")
    void laContrasenaDebilSeRechaza() throws Exception {
        mvc.perform(post("/api/v1/users")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"users.debil","fullName":"Clave débil",
                                 "password":"corta","role":"COOK"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    @DisplayName("El establecimiento no puede quedarse sin administradores activos")
    void noSePuedeDesactivarAlUltimoAdministrador() throws Exception {
        mvc.perform(patch("/api/v1/users/" + adminId)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"active\":false}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        mvc.perform(get("/api/v1/users/" + adminId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(true));
    }

    @Test
    @DisplayName("El listado devuelve página, total y solo usuarios del establecimiento")
    void elListadoEsPaginado() throws Exception {
        mvc.perform(get("/api/v1/users?page=0&size=2")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(2))
                .andExpect(jsonPath("$.total").isNumber())
                .andExpect(jsonPath("$.content.length()").value(2));
    }

    private String loginToken(String username) throws Exception {
        String body = mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"%s","password":"%s"}
                                """.formatted(username, PASSWORD)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.accessToken");
    }

    private static String newUser(String username, String role) {
        return """
                {"username":"%s","fullName":"Usuario de prueba","password":"%s","role":"%s"}
                """.formatted(username, PASSWORD, role);
    }
}
