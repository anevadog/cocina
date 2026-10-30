package com.killa.cocina.modules.audit.domain;

/**
 * Puerto de registro de auditoría (CAL-06).
 *
 * <p>La implementación escribe en la misma transacción que la operación auditada: si la operación
 * falla, no queda rastro; si tiene éxito, el registro es obligatorio.</p>
 */
public interface AuditRecorder {

    /**
     * Registra una acción sensible.
     *
     * @param establishmentId establecimiento de la operación
     * @param actorId usuario que la ejecuta; {@code null} solo en acciones del sistema
     * @param beforeJson estado anterior en JSON, o {@code null} si no aplica
     * @param afterJson estado posterior en JSON, o {@code null} si no aplica
     */
    void record(long establishmentId, Long actorId, AuditAction action, String entityType,
            Long entityId, String beforeJson, String afterJson);
}
