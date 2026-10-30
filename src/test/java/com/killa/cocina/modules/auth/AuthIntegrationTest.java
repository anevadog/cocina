package com.killa.cocina.modules.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.killa.cocina.config.EstablishmentProperties;
import com.killa.cocina.modules.users.domain.Role;
import com.killa.cocina.modules.users.domain.UserRegistration;
import com.killa.cocina.modules.users.domain.UserService;
import com.killa.cocina.support.AbstractIntegrationTest;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Contrato de autenticación contra PostgreSQL real (NFR-SEG-01, NFR-SEG-02).
 *
 * <p>Cada prueba usa su propio usuario: el contenedor y el contexto se comparten entre clases, así
 * que los nombres no pueden repetirse.</p>
 */
@AutoConfigureMockMvc
class AuthIntegrationTest extends AbstractIntegrationTest {

    private static final String PASSWORD = "contrasena-segura-2026";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private UserService users;

    @Autowired
    private EstablishmentProperties establishment;

    @Test
    @DisplayName("El login correcto devuelve access token, refresh token y caducidades, sin credenciales")
    void loginCorrectoDevuelveTokens() throws Exception {
        createUser("auth.manager", Role.KITCHEN_MANAGER);

        mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(credentials("auth.manager", PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresAt").isNotEmpty())
                .andExpect(jsonPath("$.refreshToken").isNotEmpty())
                .andExpect(jsonPath("$.refreshExpiresAt").isNotEmpty())
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
    }

    @Test
    @DisplayName("Una contraseña incorrecta devuelve 401 sin revelar si el usuario existe")
    void contrasenaIncorrectaDevuelve401() throws Exception {
        createUser("auth.wrong", Role.COOK);

        mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(credentials("auth.wrong", "otra-contrasena-larga")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));

        mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(credentials("auth.inexistente", PASSWORD)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
    }

    @Test
    @DisplayName("El quinto intento fallido bloquea la cuenta y el sexto devuelve 423 aunque la clave sea correcta")
    void cincoIntentosFallidosBloqueanLaCuenta() throws Exception {
        createUser("auth.lock", Role.COOK);

        for (int attempt = 1; attempt <= 5; attempt++) {
            mvc.perform(post("/api/v1/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(credentials("auth.lock", "fallo-" + attempt + "-suficientemente-largo")))
                    .andExpect(status().isUnauthorized());
        }

        mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(credentials("auth.lock", PASSWORD)))
                .andExpect(status().isLocked())
                .andExpect(jsonPath("$.code").value("ACCOUNT_LOCKED"));
    }

    @Test
    @DisplayName("El refresh rota el token, y reutilizar el anterior invalida toda la familia")
    void elRefreshRotaYDetectaReutilizacion() throws Exception {
        createUser("auth.rotate", Role.COOK);
        String original = refreshTokenOf("auth.rotate", PASSWORD);

        String rotated = refresh(original);

        assertThat(rotated).isNotBlank().isNotEqualTo(original);

        mvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(refreshBody(original)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_REFRESH_TOKEN"));

        // Reutilizar un token rotado revoca la familia completa: el token nuevo tampoco vale.
        mvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(refreshBody(rotated)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_REFRESH_TOKEN"));
    }

    @Test
    @DisplayName("El logout revoca el refresh token y el refresh posterior devuelve 401")
    void elLogoutRevocaElRefreshToken() throws Exception {
        createUser("auth.logout", Role.COOK);
        String refreshToken = refreshTokenOf("auth.logout", PASSWORD);

        mvc.perform(post("/api/v1/auth/logout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(refreshBody(refreshToken)))
                .andExpect(status().isNoContent());

        mvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(refreshBody(refreshToken)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_REFRESH_TOKEN"));
    }

    @Test
    @DisplayName("Sin token, cualquier endpoint de negocio responde 401 con el cuerpo de error uniforme")
    void sinTokenLaApiResponde401() throws Exception {
        mvc.perform(get("/api/v1/users"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.requestId").isNotEmpty());
    }

    @Test
    @DisplayName("Un cuerpo de login inválido devuelve 400 con detalle del campo")
    void cuerpoInvalidoDevuelve400() throws Exception {
        mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"\",\"password\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    private String refreshTokenOf(String username, String password) throws Exception {
        String body = mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(credentials(username, password)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.refreshToken");
    }

    private String refresh(String refreshToken) throws Exception {
        String body = mvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(refreshBody(refreshToken)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.refreshToken");
    }

    private void createUser(String username, Role role) {
        users.create(establishment.id(), null,
                new UserRegistration(username, "Usuario " + username, PASSWORD, role));
    }

    private static String credentials(String username, String password) {
        return """
                {"username":"%s","password":"%s"}
                """.formatted(username, password);
    }

    private static String refreshBody(String refreshToken) {
        return """
                {"refreshToken":"%s"}
                """.formatted(refreshToken);
    }
}
