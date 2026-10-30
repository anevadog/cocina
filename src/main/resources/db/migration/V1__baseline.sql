-- V1 — Base del esquema.
--
-- Establecimiento único en el MVP, pero el modelo nace preparado para varios establecimientos
-- (AGENTS.md §7.6). Todas las tablas de negocio de las fases siguientes llevarán
-- establishment_id NOT NULL con clave ajena a esta tabla.
--
-- Convenciones: snake_case plural, bigint con secuencia, instantes en UTC.
-- ddl-auto solo valida: el esquema se cambia exclusivamente con migraciones Flyway.

CREATE SEQUENCE establishments_seq START WITH 1 INCREMENT BY 1 NO CYCLE;

CREATE TABLE establishments (
    id         bigint       NOT NULL DEFAULT nextval('establishments_seq'),
    name       varchar(120) NOT NULL,
    timezone   varchar(64)  NOT NULL DEFAULT 'Europe/Madrid',
    currency   varchar(3)   NOT NULL DEFAULT 'EUR',
    active     boolean      NOT NULL DEFAULT true,
    created_at timestamptz  NOT NULL DEFAULT now(),
    updated_at timestamptz  NOT NULL DEFAULT now(),
    CONSTRAINT pk_establishments PRIMARY KEY (id),
    CONSTRAINT ck_establishments_currency CHECK (currency = upper(currency)),
    CONSTRAINT ck_establishments_timezone CHECK (timezone <> ''),
    CONSTRAINT ck_establishments_name CHECK (name <> '')
);

COMMENT ON TABLE establishments IS 'Establecimiento (tenant). Raíz de todas las tablas de negocio.';
