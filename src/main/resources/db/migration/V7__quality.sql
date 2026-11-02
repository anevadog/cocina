-- V7 — Calidad y trazabilidad: temperaturas, mermas, incidencias y auditoría protegida
-- (CAL-01..06).
--
-- Las tres tablas nacen aquí para fijar el modelo completo de F6, aunque cada bloque habilite sus
-- endpoints. Cantidades numeric(12,3), costes numeric(12,4) e instantes en UTC (AGENTS.md §7).
--
-- temperature_logs guarda una lectura con su rango. El rango es parte del registro, no una
-- configuración por equipo: así una lectura pasada conserva el criterio con el que se juzgó.
-- within_range es una columna generada por PostgreSQL para que nunca pueda contradecir a value_c.

CREATE SEQUENCE temperature_logs_seq START WITH 1 INCREMENT BY 1 NO CYCLE;
CREATE SEQUENCE waste_records_seq START WITH 1 INCREMENT BY 1 NO CYCLE;
CREATE SEQUENCE incidents_seq START WITH 1 INCREMENT BY 1 NO CYCLE;

CREATE TABLE temperature_logs (
    id               bigint        NOT NULL DEFAULT nextval('temperature_logs_seq'),
    establishment_id bigint        NOT NULL,
    kind             varchar(20)   NOT NULL,
    equipment        varchar(80)   NOT NULL,
    value_c          numeric(5,2)  NOT NULL,
    min_c            numeric(5,2)  NOT NULL,
    max_c            numeric(5,2)  NOT NULL,
    within_range     boolean       GENERATED ALWAYS AS (value_c BETWEEN min_c AND max_c) STORED,
    actor_id         bigint        NOT NULL,
    recorded_at      timestamptz   NOT NULL,
    created_at       timestamptz   NOT NULL DEFAULT now(),
    CONSTRAINT pk_temperature_logs PRIMARY KEY (id),
    CONSTRAINT fk_temperature_logs_establishment
        FOREIGN KEY (establishment_id) REFERENCES establishments (id),
    CONSTRAINT fk_temperature_logs_actor FOREIGN KEY (actor_id) REFERENCES app_users (id),
    CONSTRAINT ck_temperature_logs_kind CHECK (kind IN ('EQUIPMENT', 'PREPARATION')),
    CONSTRAINT ck_temperature_logs_equipment
        CHECK (char_length(btrim(equipment)) BETWEEN 2 AND 80),
    -- Un rango vacío o invertido no describe ningún criterio de conservación.
    CONSTRAINT ck_temperature_logs_range CHECK (min_c < max_c)
);

CREATE INDEX ix_temperature_logs_recorded ON temperature_logs (establishment_id, recorded_at DESC, id DESC);
CREATE INDEX ix_temperature_logs_alerts
    ON temperature_logs (establishment_id, recorded_at DESC, id DESC)
    WHERE within_range = false;

COMMENT ON TABLE temperature_logs IS
    'Lecturas de temperatura con su rango y alerta derivada. Una lectura fuera de rango se guarda.';
COMMENT ON COLUMN temperature_logs.within_range IS
    'Derivada de value_c y el rango; generada por la base de datos para que no pueda falsearse.';

CREATE TABLE waste_records (
    id               bigint        NOT NULL DEFAULT nextval('waste_records_seq'),
    establishment_id bigint        NOT NULL,
    product_id       bigint        NOT NULL,
    lot_id           bigint,
    quantity_base    numeric(12,3) NOT NULL,
    unit_cost        numeric(12,4) NOT NULL,
    cost             numeric(12,4) NOT NULL,
    reason           varchar(200)  NOT NULL,
    actor_id         bigint        NOT NULL,
    occurred_at      timestamptz   NOT NULL,
    created_at       timestamptz   NOT NULL DEFAULT now(),
    CONSTRAINT pk_waste_records PRIMARY KEY (id),
    CONSTRAINT fk_waste_records_establishment
        FOREIGN KEY (establishment_id) REFERENCES establishments (id),
    CONSTRAINT fk_waste_records_product FOREIGN KEY (product_id) REFERENCES products (id),
    CONSTRAINT fk_waste_records_lot FOREIGN KEY (lot_id) REFERENCES stock_lots (id),
    CONSTRAINT fk_waste_records_actor FOREIGN KEY (actor_id) REFERENCES app_users (id),
    CONSTRAINT ck_waste_records_quantity CHECK (quantity_base > 0),
    CONSTRAINT ck_waste_records_unit_cost CHECK (unit_cost >= 0),
    CONSTRAINT ck_waste_records_cost CHECK (cost >= 0),
    CONSTRAINT ck_waste_records_reason
        CHECK (char_length(btrim(reason)) BETWEEN 5 AND 200)
);

CREATE INDEX ix_waste_records_occurred ON waste_records (establishment_id, occurred_at DESC, id DESC);
CREATE INDEX ix_waste_records_product ON waste_records (product_id);
CREATE INDEX ix_waste_records_lot ON waste_records (lot_id);

COMMENT ON TABLE waste_records IS
    'Cabecera de merma. El coste se congela con el PMP vigente y los movimientos WASTE_OUT llevan su id.';

CREATE TABLE incidents (
    id               bigint        NOT NULL DEFAULT nextval('incidents_seq'),
    establishment_id bigint        NOT NULL,
    description      varchar(1000) NOT NULL,
    severity         varchar(20)   NOT NULL,
    status           varchar(20)   NOT NULL DEFAULT 'OPEN',
    resolution       varchar(500),
    opened_by        bigint        NOT NULL,
    opened_at        timestamptz   NOT NULL,
    updated_at       timestamptz   NOT NULL,
    closed_by        bigint,
    closed_at        timestamptz,
    CONSTRAINT pk_incidents PRIMARY KEY (id),
    CONSTRAINT fk_incidents_establishment
        FOREIGN KEY (establishment_id) REFERENCES establishments (id),
    CONSTRAINT fk_incidents_opened_by FOREIGN KEY (opened_by) REFERENCES app_users (id),
    CONSTRAINT fk_incidents_closed_by FOREIGN KEY (closed_by) REFERENCES app_users (id),
    CONSTRAINT ck_incidents_description
        CHECK (char_length(btrim(description)) BETWEEN 10 AND 1000),
    CONSTRAINT ck_incidents_severity CHECK (severity IN ('LOW', 'MEDIUM', 'HIGH', 'CRITICAL')),
    CONSTRAINT ck_incidents_status CHECK (status IN ('OPEN', 'IN_PROGRESS', 'CLOSED')),
    CONSTRAINT ck_incidents_resolution
        CHECK (resolution IS NULL OR char_length(btrim(resolution)) BETWEEN 5 AND 500),
    -- Cerrar exige resolución, usuario y fecha; una incidencia abierta no puede tener datos de cierre.
    CONSTRAINT ck_incidents_closure CHECK (
        (status = 'CLOSED' AND resolution IS NOT NULL AND closed_by IS NOT NULL AND closed_at IS NOT NULL)
        OR (status <> 'CLOSED' AND resolution IS NULL AND closed_by IS NULL AND closed_at IS NULL)
    )
);

CREATE INDEX ix_incidents_status ON incidents (establishment_id, status, opened_at DESC, id DESC);
CREATE INDEX ix_incidents_opened_at ON incidents (establishment_id, opened_at DESC, id DESC);

COMMENT ON TABLE incidents IS
    'Incidencias de calidad. Sin reapertura en el MVP: el histórico conserva el cierre original.';

-- CAL-06: audit_logs se documenta como append-only, pero hasta V7 solo lo garantizaba el código.
-- El trigger lo hace cumplir también ante SQL directo, igual que el kardex (V4).
CREATE FUNCTION audit_logs_immutable() RETURNS trigger
LANGUAGE plpgsql AS $$
BEGIN
    RAISE EXCEPTION 'audit_logs es append-only: no se modifica ni se borra'
        USING ERRCODE = 'integrity_constraint_violation';
END;
$$;

CREATE TRIGGER trg_audit_logs_immutable
    BEFORE UPDATE OR DELETE ON audit_logs
    FOR EACH ROW EXECUTE FUNCTION audit_logs_immutable();

COMMENT ON FUNCTION audit_logs_immutable() IS
    'Bloquea UPDATE y DELETE sobre audit_logs incluso para el propietario de la tabla.';
