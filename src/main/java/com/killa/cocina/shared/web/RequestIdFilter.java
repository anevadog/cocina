package com.killa.cocina.shared.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Asigna un identificador de correlación a cada petición (NFR-OBS-01).
 *
 * <p>Si el cliente envía una cabecera {@code X-Request-Id} con un formato aceptable se reutiliza;
 * en cualquier otro caso se genera uno nuevo. El identificador viaja en la respuesta, en el MDC de
 * los logs estructurados y en el cuerpo de error uniforme (PLA-02).</p>
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestIdFilter extends OncePerRequestFilter {

    public static final String HEADER_NAME = "X-Request-Id";

    public static final String MDC_KEY = "requestId";

    /** Atributo de petición donde se publica el identificador para el resto de la aplicación. */
    public static final String ATTRIBUTE_NAME = RequestIdFilter.class.getName() + ".requestId";

    /** Solo se aceptan identificadores acotados para no inyectar contenido arbitrario en los logs. */
    private static final Pattern ACCEPTED_FORMAT = Pattern.compile("[A-Za-z0-9._-]{8,64}");

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        String requestId = resolve(request.getHeader(HEADER_NAME));
        request.setAttribute(ATTRIBUTE_NAME, requestId);
        response.setHeader(HEADER_NAME, requestId);
        MDC.put(MDC_KEY, requestId);
        try {
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove(MDC_KEY);
        }
    }

    /**
     * Devuelve el identificador de correlación de la petición actual.
     *
     * @return el identificador asignado por el filtro, o {@code null} si la petición no pasó por él
     */
    public static String currentRequestId(HttpServletRequest request) {
        Object requestId = request.getAttribute(ATTRIBUTE_NAME);
        return requestId instanceof String value ? value : null;
    }

    static String resolve(String incoming) {
        if (incoming != null && ACCEPTED_FORMAT.matcher(incoming).matches()) {
            return incoming;
        }
        return UUID.randomUUID().toString();
    }
}
