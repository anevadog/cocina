package com.killa.cocina.shared.error;

/** Códigos de error estables expuestos por la API. */
public enum ErrorCode {

    /** La petición no cumple las reglas de validación de los datos de entrada. */
    VALIDATION_ERROR,

    /** El cuerpo de la petición no se puede interpretar. */
    MALFORMED_REQUEST,

    /** El recurso solicitado no existe. */
    RESOURCE_NOT_FOUND,

    /** El método HTTP no está permitido para el recurso. */
    METHOD_NOT_ALLOWED,

    /** El tipo de contenido enviado no está soportado. */
    UNSUPPORTED_MEDIA_TYPE,

    /** Error inesperado. No se expone detalle interno. */
    INTERNAL_ERROR
}
