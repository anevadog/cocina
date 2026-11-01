package com.killa.cocina.shared.health;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class ReadinessControllerTest {

    private MockMvc mockMvc;

    private static MockMvc conIndicadores(ReadinessIndicator... indicators) {
        return MockMvcBuilders.standaloneSetup(new ReadinessController(List.of(indicators)))
                .build();
    }

    private static ReadinessIndicator indicador(String nombre, ReadinessStatus estado) {
        return new ReadinessIndicator() {
            @Override
            public String name() {
                return nombre;
            }

            @Override
            public ReadinessStatus status() {
                return estado;
            }
        };
    }

    @Test
    void sinDependenciasRegistradasRespondeReady() throws Exception {
        mockMvc = conIndicadores();

        mockMvc.perform(get("/ready"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("READY"))
                .andExpect(jsonPath("$.checks").isEmpty());
    }

    @Test
    void responde200CuandoTodasLasDependenciasEstanArriba() throws Exception {
        mockMvc = conIndicadores(indicador("postgres", ReadinessStatus.UP));

        mockMvc.perform(get("/ready"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("READY"))
                .andExpect(jsonPath("$.checks[0].name").value("postgres"))
                .andExpect(jsonPath("$.checks[0].status").value("UP"));
    }

    @Test
    void responde503CuandoAlgunaDependenciaEstaCaida() throws Exception {
        mockMvc = conIndicadores(
                indicador("postgres", ReadinessStatus.UP), indicador("migraciones", ReadinessStatus.DOWN));

        mockMvc.perform(get("/ready"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.status").value("NOT_READY"))
                .andExpect(jsonPath("$.checks[0].name").value("migraciones"))
                .andExpect(jsonPath("$.checks[1].name").value("postgres"));
    }
}
