package com.killa.cocina.shared.error;

import com.killa.cocina.shared.web.RequestIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import java.util.Comparator;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * Traduce cualquier excepción a un {@link ApiError} uniforme (PLA-02).
 *
 * <p>Los mensajes están en español y no exponen detalles internos: en los errores 5xx solo se
 * registra la traza en el log, correlacionada por {@code requestId}.</p>
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> onInvalidBody(
            MethodArgumentNotValidException exception, HttpServletRequest request) {
        List<ApiError.FieldViolation> details = exception.getBindingResult().getFieldErrors().stream()
                .map(error -> new ApiError.FieldViolation(error.getField(), defaultMessage(error.getDefaultMessage())))
                .sorted(Comparator.comparing(ApiError.FieldViolation::field))
                .toList();
        return build(
                HttpStatus.BAD_REQUEST,
                ErrorCode.VALIDATION_ERROR,
                "La petición contiene datos no válidos.",
                details,
                request);
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<ApiError> onInvalidParameter(
            HandlerMethodValidationException exception, HttpServletRequest request) {
        return build(
                HttpStatus.BAD_REQUEST,
                ErrorCode.VALIDATION_ERROR,
                "Los parámetros de la petición no son válidos.",
                List.of(),
                request);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiError> onConstraintViolation(
            ConstraintViolationException exception, HttpServletRequest request) {
        List<ApiError.FieldViolation> details = exception.getConstraintViolations().stream()
                .map(violation -> new ApiError.FieldViolation(
                        violation.getPropertyPath().toString(), defaultMessage(violation.getMessage())))
                .sorted(Comparator.comparing(ApiError.FieldViolation::field))
                .toList();
        return build(
                HttpStatus.BAD_REQUEST,
                ErrorCode.VALIDATION_ERROR,
                "La petición contiene datos no válidos.",
                details,
                request);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiError> onTypeMismatch(
            MethodArgumentTypeMismatchException exception, HttpServletRequest request) {
        List<ApiError.FieldViolation> details =
                List.of(new ApiError.FieldViolation(exception.getName(), "el formato del valor no es válido"));
        return build(
                HttpStatus.BAD_REQUEST,
                ErrorCode.VALIDATION_ERROR,
                "Los parámetros de la petición no son válidos.",
                details,
                request);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> onUnreadableBody(
            HttpMessageNotReadableException exception, HttpServletRequest request) {
        return build(
                HttpStatus.BAD_REQUEST,
                ErrorCode.MALFORMED_REQUEST,
                "El cuerpo de la petición no se puede interpretar.",
                List.of(),
                request);
    }

    @ExceptionHandler({NoResourceFoundException.class, NoHandlerFoundException.class})
    public ResponseEntity<ApiError> onNotFound(Exception exception, HttpServletRequest request) {
        return build(
                HttpStatus.NOT_FOUND,
                ErrorCode.RESOURCE_NOT_FOUND,
                "El recurso solicitado no existe.",
                List.of(),
                request);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiError> onMethodNotAllowed(
            HttpRequestMethodNotSupportedException exception, HttpServletRequest request) {
        return build(
                HttpStatus.METHOD_NOT_ALLOWED,
                ErrorCode.METHOD_NOT_ALLOWED,
                "El método HTTP no está permitido para este recurso.",
                List.of(),
                request);
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ApiError> onUnsupportedMediaType(
            HttpMediaTypeNotSupportedException exception, HttpServletRequest request) {
        return build(
                HttpStatus.UNSUPPORTED_MEDIA_TYPE,
                ErrorCode.UNSUPPORTED_MEDIA_TYPE,
                "El tipo de contenido de la petición no está soportado.",
                List.of(),
                request);
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ApiError> onResponseStatus(ResponseStatusException exception, HttpServletRequest request) {
        HttpStatus status = HttpStatus.resolve(exception.getStatusCode().value());
        if (status == null) {
            status = HttpStatus.INTERNAL_SERVER_ERROR;
        }
        ErrorCode code = status.is4xxClientError() ? ErrorCode.RESOURCE_NOT_FOUND : ErrorCode.INTERNAL_ERROR;
        return build(status, code, defaultMessage(exception.getReason()), List.of(), request);
    }

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ApiError> onApiException(ApiException exception, HttpServletRequest request) {
        HttpStatus status = HttpStatus.resolve(exception.code().httpStatus());
        if (status == null) {
            status = HttpStatus.INTERNAL_SERVER_ERROR;
        }
        return build(status, exception.code(), defaultMessage(exception.getMessage()), exception.details(), request);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiError> onAccessDenied(AccessDeniedException exception, HttpServletRequest request) {
        return build(
                HttpStatus.FORBIDDEN,
                ErrorCode.FORBIDDEN,
                "No tienes permiso para realizar esta operación.",
                List.of(),
                request);
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ApiError> onAuthentication(AuthenticationException exception, HttpServletRequest request) {
        return build(
                HttpStatus.UNAUTHORIZED,
                ErrorCode.UNAUTHORIZED,
                "Necesitas autenticarte para acceder a este recurso.",
                List.of(),
                request);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> onUnexpected(Exception exception, HttpServletRequest request) {
        String requestId = resolveRequestId(request);
        log.error("Error no controlado [requestId={}]", requestId, exception);
        return build(
                HttpStatus.INTERNAL_SERVER_ERROR,
                ErrorCode.INTERNAL_ERROR,
                "Se ha producido un error inesperado. Contacta con soporte indicando el requestId.",
                List.of(),
                request);
    }

    private ResponseEntity<ApiError> build(
            HttpStatus status,
            ErrorCode code,
            String message,
            List<ApiError.FieldViolation> details,
            HttpServletRequest request) {
        ApiError body = new ApiError(code.name(), message, details, resolveRequestId(request));
        return ResponseEntity.status(status).body(body);
    }

    private static String resolveRequestId(HttpServletRequest request) {
        String requestId = RequestIdFilter.currentRequestId(request);
        return requestId == null ? "desconocido" : requestId;
    }

    private static String defaultMessage(String message) {
        return message == null || message.isBlank() ? "valor no válido" : message;
    }
}
