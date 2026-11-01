package com.killa.cocina.shared.web;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class RequestIdFilterTest {

    private final RequestIdFilter filter = new RequestIdFilter();

    @AfterEach
    void limpiarMdc() {
        MDC.clear();
    }

    @Test
    void generaIdentificadorCuandoNoVieneEnLaCabecera() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/health");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        String requestId = response.getHeader(RequestIdFilter.HEADER_NAME);
        assertThat(requestId).isNotBlank();
        assertThat(request.getAttribute(RequestIdFilter.ATTRIBUTE_NAME)).isEqualTo(requestId);
        assertThat(RequestIdFilter.currentRequestId(request)).isEqualTo(requestId);
    }

    @Test
    void conservaElIdentificadorValidoDelCliente() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/health");
        request.addHeader(RequestIdFilter.HEADER_NAME, "abc-123-XYZ_9");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertThat(response.getHeader(RequestIdFilter.HEADER_NAME)).isEqualTo("abc-123-XYZ_9");
    }

    @Test
    void descartaIdentificadoresConFormatoNoAceptado() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/health");
        request.addHeader(RequestIdFilter.HEADER_NAME, "no valido;rm -rf");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertThat(response.getHeader(RequestIdFilter.HEADER_NAME)).isNotEqualTo("no valido;rm -rf");
    }

    @Test
    void publicaElIdentificadorEnElMdcYSoloDuranteLaPeticion() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/health");
        MockHttpServletResponse response = new MockHttpServletResponse();
        String[] dentroDeLaPeticion = new String[1];

        filter.doFilter(
                request,
                response,
                (servletRequest, servletResponse) -> dentroDeLaPeticion[0] = MDC.get(RequestIdFilter.MDC_KEY));

        assertThat(dentroDeLaPeticion[0]).isEqualTo(response.getHeader(RequestIdFilter.HEADER_NAME));
        assertThat(MDC.get(RequestIdFilter.MDC_KEY)).isNull();
    }
}
