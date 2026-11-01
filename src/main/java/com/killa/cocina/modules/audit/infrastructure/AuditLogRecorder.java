package com.killa.cocina.modules.audit.infrastructure;

import com.killa.cocina.modules.audit.domain.AuditAction;
import com.killa.cocina.modules.audit.domain.AuditRecorder;
import java.sql.Types;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** Escritura append-only de auditoría sobre {@code audit_logs} (CAL-06). */
@Repository
public class AuditLogRecorder implements AuditRecorder {

    private static final String INSERT = """
            insert into audit_logs (establishment_id, actor_id, action, entity_type, entity_id,
                                    before_json, after_json, occurred_at)
            values (?, ?, ?, ?, ?, cast(? as jsonb), cast(? as jsonb), now())
            """;

    /**
     * Tipos explícitos: con un valor {@code null} PostgreSQL no puede inferirlos y el
     * {@code cast(? as jsonb)} debe seguir siendo válido.
     */
    private static final int[] TYPES = {
        Types.BIGINT, Types.BIGINT, Types.VARCHAR, Types.VARCHAR, Types.BIGINT, Types.VARCHAR, Types.VARCHAR
    };

    private final JdbcTemplate jdbcTemplate;

    public AuditLogRecorder(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void record(
            long establishmentId,
            Long actorId,
            AuditAction action,
            String entityType,
            Long entityId,
            String beforeJson,
            String afterJson) {
        jdbcTemplate.update(
                INSERT,
                new Object[] {establishmentId, actorId, action.name(), entityType, entityId, beforeJson, afterJson},
                TYPES);
    }
}
