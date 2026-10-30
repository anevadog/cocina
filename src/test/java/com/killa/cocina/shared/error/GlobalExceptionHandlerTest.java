package com.killa.cocina.shared.error;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.killa.cocina.shared.web.RequestIdFilter;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Verifica el contrato de error uniforme (PLA-02) contra un controlador de prueba.
 */
class GlobalExceptionHandlerTest {

    private MockMvc mockMvc;

    @BeforeEach
    void prepararMockMvc() {
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        mockMvc = MockMvcBuilders.standaloneSetup(new ProbeController())
                .setControllerAdvice(new GlobalExceptionHandler())
                .setValidator(validator)
                .addFilters(new RequestIdFilter())
                .build();
    }

    @Test
    void unCuerpoInvalidoDevuelveErrorDeValidacionConElCampoAfectado() throws Exception {
        mockMvc.perform(post("/api/v1/probe")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.message").value("La petición contiene datos no válidos."))
                .andExpect(jsonPath("$.details[0].field").value("nombre"))
                .andExpect(jsonPath("$.details[0].message").value("es obligatorio"));
    }

    @Test
    void elErrorSiempreIncluyeElRequestIdDeLaPeticion() throws Exception {
        mockMvc.perform(get("/api/v1/probe/boom")
                        .header(RequestIdFilter.HEADER_NAME, "prueba-123456"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.requestId").value("prueba-123456"));
    }

    @Test
    void unErrorInesperadoNoFiltraDetalleInterno() throws Exception {
        mockMvc.perform(get("/api/v1/probe/boom"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.message").value(not(containsString("secreto"))))
                .andExpect(jsonPath("$.details").isEmpty())
                .andExpect(jsonPath("$.requestId").value(matchesPattern("[0-9a-f-]{36}")));
    }

    @Test
    void unParametroConTipoIncorrectoDevuelveErrorDeValidacion() throws Exception {
        mockMvc.perform(get("/api/v1/probe/abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.details[0].field").value("id"));
    }

    @Test
    void unMetodoNoSoportadoDevuelve405() throws Exception {
        mockMvc.perform(delete("/api/v1/probe/1"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.code").value("METHOD_NOT_ALLOWED"));
    }

    @Test
    void unCuerpoIlegibleDevuelveErrorDeFormato() throws Exception {
        mockMvc.perform(post("/api/v1/probe")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ esto no es json }"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));
    }

    record ProbeRequest(@NotBlank(message = "es obligatorio") String nombre) {
    }

    @RestController
    @RequestMapping("/api/v1/probe")
    static class ProbeController {

        @GetMapping("/boom")
        String boom() {
            throw new IllegalStateException("detalle interno secreto");
        }

        @GetMapping("/{id}")
        String porId(@PathVariable Long id) {
            return "ok";
        }

        @PostMapping
        String validar(@Valid @RequestBody ProbeRequest body) {
            return "ok";
        }
    }
}
