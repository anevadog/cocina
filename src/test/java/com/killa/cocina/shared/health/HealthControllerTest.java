package com.killa.cocina.shared.health;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.killa.cocina.shared.web.RequestIdFilter;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class HealthControllerTest {

    private static final Instant NOW = Instant.parse("2026-10-30T21:30:00Z");

    private MockMvc mockMvc;

    @BeforeEach
    void prepararMockMvc() {
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        mockMvc = MockMvcBuilders.standaloneSetup(new HealthController(clock, "cocina-api"))
                .addFilters(new RequestIdFilter())
                .build();
    }

    @Test
    void devuelveElEstadoDelProcesoConFechaEnUtc() throws Exception {
        mockMvc.perform(get("/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.service").value("cocina-api"))
                .andExpect(jsonPath("$.timestamp").value("2026-10-30T21:30:00Z"));
    }

    @Test
    void laRespuestaIncluyeElRequestIdParaCorrelacion() throws Exception {
        mockMvc.perform(get("/health"))
                .andExpect(status().isOk())
                .andExpect(header().exists(RequestIdFilter.HEADER_NAME));
    }
}
