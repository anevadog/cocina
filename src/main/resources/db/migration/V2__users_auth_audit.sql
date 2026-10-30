-- V2 — Usuarios, sesiones y auditoría (PER-01, PER-04, CAL-06).
--
-- Convenciones de V1: snake_case plural, bigint con secuencia, instantes en UTC.
-- Los roles se validan también en la base de datos: añadir un rol exige migración y cambio en el
-- enum Role, para que no pueda existir un rol que el código no sepa interpretar.

CREATE SEQUENCE app_users_seq START WITH 1 INCREMENT BY 1 NO CYCLE;
CREATE SEQUENCE refresh_tokens_seq START WITH 1 INCREMENT BY 1 NO CYCLE;
CREATE SEQUENCE audit_logs_seq START WITH 1 INCREMENT BY 1 NO CYCLE;

CREATE TABLE app_users (
    id              bigint       NOT NULL DEFAULT nextval('app_users_seq'),
    establishment_id bigint      NOT NULL,
    username        varchar(60)  NOT NULL,
    full_name       varchar(120) NOT NULL,
    password_hash   varchar(255) NOT NULL,
    role            varchar(30)  NOT NULL,
    active          boolean      NOT NULL DEFAULT true,
    failed_attempts integer      NOT NULL DEFAULT 0,
    locked_until    timestamptz,
    created_at      timestamptz  NOT NULL DEFAULT now(),
    updated_at      timestamptz  NOT NULL DEFAULT now(),
    CONSTRAINT pk_app_users PRIMARY KEY (id),
    CONSTRAINT fk_app_users_establishment FOREIGN KEY (establishment_id) REFERENCES establishments (id),
    CONSTRAINT uk_app_users_username UNIQUE (establishment_id, username),
    CONSTRAINT ck_app_users_username CHECK (username = lower(username) AND username <> ''),
    CONSTRAINT ck_app_users_full_name CHECK (full_name <> ''),
    CONSTRAINT ck_app_users_role CHECK (role IN ('ADMIN', 'KITCHEN_MANAGER', 'COOK', 'STOREKEEPER')),
    CONSTRAINT ck_app_users_failed_attempts CHECK (failed_attempts >= 0)
);

CREATE INDEX ix_app_users_establishment ON app_users (establishment_id);

COMMENT ON TABLE app_users IS 'Usuarios del establecimiento. Nunca se borran: se desactivan.';

CREATE TABLE refresh_tokens (
    id               bigint      NOT NULL DEFAULT nextval('refresh_tokens_seq'),
    establishment_id bigint      NOT NULL,
    user_id          bigint      NOT NULL,
    token_hash       varchar(64) NOT NULL,
    expires_at       timestamptz NOT NULL,
    revoked_at       timestamptz,
    replaced_by      bigint,
    created_at       timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT pk_refresh_tokens PRIMARY KEY (id),
    CONSTRAINT fk_refresh_tokens_establishment FOREIGN KEY (establishment_id) REFERENCES establishments (id),
    CONSTRAINT fk_refresh_tokens_user FOREIGN KEY (user_id) REFERENCES app_users (id),
    CONSTRAINT fk_refresh_tokens_replaced_by FOREIGN KEY (replaced_by) REFERENCES refresh_tokens (id),
    CONSTRAINT uk_refresh_tokens_hash UNIQUE (token_hash)
);

CREATE INDEX ix_refresh_tokens_user ON refresh_tokens (user_id);

COMMENT ON TABLE refresh_tokens IS 'Refresh tokens rotatorios: se guarda el hash SHA-256, nunca el valor.';

CREATE TABLE audit_logs (
    id               bigint      NOT NULL DEFAULT nextval('audit_logs_seq'),
    establishment_id bigint      NOT NULL,
    actor_id         bigint,
    action           varchar(60) NOT NULL,
    entity_type      varchar(60) NOT NULL,
    entity_id        bigint,
    before_json      jsonb,
    after_json       jsonb,
    occurred_at      timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT pk_audit_logs PRIMARY KEY (id),
    CONSTRAINT fk_audit_logs_establishment FOREIGN KEY (establishment_id) REFERENCES establishments (id),
    CONSTRAINT fk_audit_logs_actor FOREIGN KEY (actor_id) REFERENCES app_users (id)
);

CREATE INDEX ix_audit_logs_occurred_at ON audit_logs (occurred_at);
CREATE INDEX ix_audit_logs_entity ON audit_logs (entity_type, entity_id);

COMMENT ON TABLE audit_logs IS 'Registro append-only de acciones sensibles (CAL-06). Sin endpoint de edición.';
