-- V6 — Producción: plan diario, ítems y batches (PRD-01..05, REC-06, ECO-03).
--
-- El plan es la unidad de planificación (fecha + servicio, único por establecimiento) y el batch la
-- unidad de ejecución. El stock se descuenta al pasar el batch a READY, con los productos bloqueados
-- en orden de product_id (ADR-036, ADR-037).
--
-- Igual que el kardex y las versiones publicadas, el histórico de producción no se borra: los
-- triggers rechazan borrar batches y modificar ítems que ya tienen ejecución.

CREATE SEQUENCE production_plans_seq START WITH 1 INCREMENT BY 1 NO CYCLE;
CREATE SEQUENCE production_plan_items_seq START WITH 1 INCREMENT BY 1 NO CYCLE;
CREATE SEQUENCE production_batches_seq START WITH 1 INCREMENT BY 1 NO CYCLE;

CREATE TABLE production_plans (
    id               bigint      NOT NULL DEFAULT nextval('production_plans_seq'),
    establishment_id bigint      NOT NULL,
    service_date     date        NOT NULL,
    service_type     varchar(10) NOT NULL,
    status           varchar(20) NOT NULL DEFAULT 'PENDING',
    created_by       bigint      NOT NULL,
    created_at       timestamptz NOT NULL DEFAULT now(),
    updated_at       timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT pk_production_plans PRIMARY KEY (id),
    CONSTRAINT fk_production_plans_establishment
        FOREIGN KEY (establishment_id) REFERENCES establishments (id),
    CONSTRAINT fk_production_plans_created_by FOREIGN KEY (created_by) REFERENCES app_users (id),
    -- PRD-01: un único plan por fecha y servicio.
    CONSTRAINT uk_production_plans_service UNIQUE (establishment_id, service_date, service_type),
    CONSTRAINT ck_production_plans_service CHECK (service_type IN ('LUNCH', 'DINNER')),
    CONSTRAINT ck_production_plans_status
        CHECK (status IN ('PENDING', 'IN_PROGRESS', 'READY', 'SERVED', 'CANCELLED'))
);

CREATE INDEX ix_production_plans_date
    ON production_plans (establishment_id, service_date, service_type);

COMMENT ON TABLE production_plans IS
    'Plan de producción por fecha y servicio. El estado se recalcula desde sus batches.';

CREATE TABLE production_plan_items (
    id                bigint  NOT NULL DEFAULT nextval('production_plan_items_seq'),
    establishment_id  bigint  NOT NULL,
    plan_id           bigint  NOT NULL,
    recipe_version_id bigint  NOT NULL,
    planned_rations   integer NOT NULL,
    CONSTRAINT pk_production_plan_items PRIMARY KEY (id),
    CONSTRAINT fk_production_plan_items_establishment
        FOREIGN KEY (establishment_id) REFERENCES establishments (id),
    CONSTRAINT fk_production_plan_items_plan FOREIGN KEY (plan_id) REFERENCES production_plans (id),
    CONSTRAINT fk_production_plan_items_version
        FOREIGN KEY (recipe_version_id) REFERENCES recipe_versions (id),
    CONSTRAINT uk_production_plan_items_version UNIQUE (plan_id, recipe_version_id),
    CONSTRAINT ck_production_plan_items_rations CHECK (planned_rations BETWEEN 1 AND 10000)
);

CREATE INDEX ix_production_plan_items_plan ON production_plan_items (plan_id);
CREATE INDEX ix_production_plan_items_version ON production_plan_items (recipe_version_id);

COMMENT ON TABLE production_plan_items IS
    'Receta (versión publicada) y raciones planificadas para un servicio.';

CREATE TABLE production_batches (
    id                bigint      NOT NULL DEFAULT nextval('production_batches_seq'),
    establishment_id  bigint      NOT NULL,
    plan_item_id      bigint      NOT NULL,
    recipe_version_id bigint      NOT NULL,
    planned_rations   integer     NOT NULL,
    rations           integer     NOT NULL,
    status            varchar(20) NOT NULL,
    started_at        timestamptz NOT NULL,
    started_by        bigint      NOT NULL,
    finished_at       timestamptz,
    finished_by       bigint,
    deviation_reason  varchar(200),
    created_at        timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT pk_production_batches PRIMARY KEY (id),
    CONSTRAINT fk_production_batches_establishment
        FOREIGN KEY (establishment_id) REFERENCES establishments (id),
    CONSTRAINT fk_production_batches_item
        FOREIGN KEY (plan_item_id) REFERENCES production_plan_items (id),
    CONSTRAINT fk_production_batches_version
        FOREIGN KEY (recipe_version_id) REFERENCES recipe_versions (id),
    CONSTRAINT fk_production_batches_started_by FOREIGN KEY (started_by) REFERENCES app_users (id),
    CONSTRAINT fk_production_batches_finished_by FOREIGN KEY (finished_by) REFERENCES app_users (id),
    -- MVP: una ejecución por ítem de plan (ADR-036).
    CONSTRAINT uk_production_batches_item UNIQUE (plan_item_id),
    CONSTRAINT ck_production_batches_status
        CHECK (status IN ('IN_PROGRESS', 'READY', 'SERVED', 'CANCELLED')),
    CONSTRAINT ck_production_batches_rations
        CHECK (rations BETWEEN 1 AND 10000 AND planned_rations BETWEEN 1 AND 10000),
    CONSTRAINT ck_production_batches_finished CHECK (
        (status IN ('READY', 'SERVED')
            AND finished_at IS NOT NULL AND finished_by IS NOT NULL)
        OR (status IN ('IN_PROGRESS', 'CANCELLED')
            AND finished_at IS NULL AND finished_by IS NULL)),
    CONSTRAINT ck_production_batches_reason
        CHECK (deviation_reason IS NULL
            OR char_length(btrim(deviation_reason)) BETWEEN 5 AND 200),
    -- PRD-05 en la propia base de datos: desviación distinta de cero exige motivo.
    CONSTRAINT ck_production_batches_deviation CHECK (
        status NOT IN ('READY', 'SERVED')
        OR rations = planned_rations
        OR deviation_reason IS NOT NULL)
);

CREATE INDEX ix_production_batches_establishment
    ON production_batches (establishment_id, status);

COMMENT ON TABLE production_batches IS
    'Ejecución real de un ítem de plan. El stock se descuenta al pasar a READY (PRD-03).';

CREATE FUNCTION production_plan_items_guard() RETURNS trigger
LANGUAGE plpgsql AS $$
DECLARE
    version_status varchar(20);
BEGIN
    IF TG_OP IN ('UPDATE', 'DELETE') THEN
        IF EXISTS (SELECT 1 FROM production_batches WHERE plan_item_id = OLD.id) THEN
            RAISE EXCEPTION 'Un ítem de plan con producción registrada no se modifica'
                USING ERRCODE = 'integrity_constraint_violation';
        END IF;
    END IF;

    IF TG_OP IN ('INSERT', 'UPDATE') THEN
        SELECT status INTO version_status FROM recipe_versions WHERE id = NEW.recipe_version_id;
        IF version_status IS NULL OR version_status <> 'PUBLISHED' THEN
            RAISE EXCEPTION 'Solo se planifica una versión publicada de la receta'
                USING ERRCODE = 'integrity_constraint_violation';
        END IF;
    END IF;

    IF TG_OP = 'DELETE' THEN
        RETURN OLD;
    END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_production_plan_items_guard
    BEFORE INSERT OR UPDATE OR DELETE ON production_plan_items
    FOR EACH ROW EXECUTE FUNCTION production_plan_items_guard();

COMMENT ON FUNCTION production_plan_items_guard() IS
    'Solo admite versiones publicadas y congela el ítem en cuanto tiene un batch.';

CREATE FUNCTION production_batches_guard() RETURNS trigger
LANGUAGE plpgsql AS $$
BEGIN
    IF TG_OP = 'DELETE' THEN
        RAISE EXCEPTION 'El histórico de producción no se borra'
            USING ERRCODE = 'integrity_constraint_violation';
    END IF;

    IF OLD.status IN ('READY', 'SERVED', 'CANCELLED') THEN
        IF NOT (
            OLD.status = 'READY'
            AND NEW.status = 'SERVED'
            AND NEW.establishment_id = OLD.establishment_id
            AND NEW.plan_item_id = OLD.plan_item_id
            AND NEW.recipe_version_id = OLD.recipe_version_id
            AND NEW.planned_rations = OLD.planned_rations
            AND NEW.rations = OLD.rations
            AND NEW.started_at = OLD.started_at
            AND NEW.started_by = OLD.started_by
            AND NEW.finished_at = OLD.finished_at
            AND NEW.finished_by = OLD.finished_by
            AND NEW.deviation_reason IS NOT DISTINCT FROM OLD.deviation_reason
        ) THEN
            RAISE EXCEPTION 'Un batch terminado o cancelado solo puede pasar de READY a SERVED'
                USING ERRCODE = 'integrity_constraint_violation';
        END IF;
    END IF;

    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_production_batches_guard
    BEFORE UPDATE OR DELETE ON production_batches
    FOR EACH ROW EXECUTE FUNCTION production_batches_guard();

COMMENT ON FUNCTION production_batches_guard() IS
    'El batch se edita mientras está en preparación; después solo avanza a SERVED.';
