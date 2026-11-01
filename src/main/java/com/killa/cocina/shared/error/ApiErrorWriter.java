package com.killa.cocina.shared.error;

import com.killa.cocina.shared.web.RequestIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/**
 * Escribe el cuerpo de error uniforme desde fuera del ciclo de Spring MVC (PLA-02).
 *
 * <p>La usan el punto de entrada de autenticación y el manejador de acceso denegado, que actúan en
 * la cadena de filtros, antes de que exista un controlador.</p>
 */
@Component
public class ApiErrorWriter {

    private final ObjectMapper objectMapper;

    public ApiErrorWriter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public void write(
            HttpServletRequest request, HttpServletResponse response, HttpStatus status, ErrorCode code, String message)
            throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        String requestId = RequestIdFilter.currentRequestId(request);
        ApiError body = new ApiError(code.name(), message, List.of(), requestId == null ? "desconocido" : requestId);
        objectMapper.writeValue(response.getOutputStream(), body);
    }
}
