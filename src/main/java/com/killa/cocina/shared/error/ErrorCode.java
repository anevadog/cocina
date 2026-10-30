package com.killa.cocina.shared.error;

/**
 * Códigos de error estables expuestos por la API.
 *
 * <p>Cada código lleva su estado HTTP para que el dominio pueda señalar el motivo del fallo sin
 * depender de Spring: el nombre del código viaja en el contrato y el estado se resuelve aquí.</p>
 */
public enum ErrorCode {

    /** La petición no cumple las reglas de validación de los datos de entrada. */
    VALIDATION_ERROR(400),

    /** El cuerpo de la petición no se puede interpretar. */
    MALFORMED_REQUEST(400),

    /** La contraseña propuesta no cumple la política mínima. */
    WEAK_PASSWORD(400),

    /** Falta la autenticación o el token no es válido. */
    UNAUTHORIZED(401),

    /** Usuario o contraseña incorrectos. */
    INVALID_CREDENTIALS(401),

    /** El refresh token no existe, ha caducado o ya se usó. */
    INVALID_REFRESH_TOKEN(401),

    /** El usuario autenticado no tiene el permiso necesario. */
    FORBIDDEN(403),

    /** El recurso solicitado no existe. */
    RESOURCE_NOT_FOUND(404),

    /** El método HTTP no está permitido para el recurso. */
    METHOD_NOT_ALLOWED(405),

    /** El nombre de usuario ya está en uso en el establecimiento. */
    USERNAME_TAKEN(409),

    /** La cuenta está bloqueada temporalmente por intentos fallidos. */
    ACCOUNT_LOCKED(423),

    /** El tipo de contenido enviado no está soportado. */
    UNSUPPORTED_MEDIA_TYPE(415),

    /** Error inesperado. No se expone detalle interno. */
    INTERNAL_ERROR(500);

    private final int httpStatus;

    ErrorCode(int httpStatus) {
        this.httpStatus = httpStatus;
    }

    public int httpStatus() {
        return httpStatus;
    }
}
