-- V5 — Recetas: cabecera, versiones y composición (REC-01..05, ECO-01).
--
-- La cantidad de cada ingrediente se guarda POR RACIÓN y en la unidad base del producto
-- (numeric(12,3)); las conversiones se aplican en el borde, igual que en el inventario
-- (AGENTS.md §7.2). El coste del lote base se deriva de base_rations (ADR-033).
--
-- Una versión publicada es inmutable: no existe endpoint de edición y un trigger la protege en la
-- propia base de datos, como el kardex (regla 7.3, REC-05). El borrador sí se edita en sitio, y
-- solo puede haber uno por receta (índice único parcial).

CREATE SEQUENCE recipes_seq START WITH 1 INCREMENT BY 1 NO CYCLE;
CREATE SEQUENCE recipe_versions_seq START WITH 1 INCREMENT BY 1 NO CYCLE;
CREATE SEQUENCE recipe_items_seq START WITH 1 INCREMENT BY 1 NO CYCLE;

CREATE TABLE recipes (
    id                 bigint       NOT NULL DEFAULT nextval('recipes_seq'),
    establishment_id   bigint       NOT NULL,
    name               varchar(120) NOT NULL,
    category           varchar(80),
    active             boolean      NOT NULL DEFAULT true,
    current_version_id bigint,
    created_at         timestamptz  NOT NULL DEFAULT now(),
    updated_at         timestamptz  NOT NULL DEFAULT now(),
    CONSTRAINT pk_recipes PRIMARY KEY (id),
    CONSTRAINT fk_recipes_establishment
        FOREIGN KEY (establishment_id) REFERENCES establishments (id),
    CONSTRAINT uk_recipes_name UNIQUE (establishment_id, name),
    CONSTRAINT ck_recipes_name CHECK (name <> ''),
    CONSTRAINT ck_recipes_category
        CHECK (category IS NULL OR char_length(btrim(category)) BETWEEN 2 AND 80)
);

CREATE INDEX ix_recipes_establishment ON recipes (establishment_id, active, name);

COMMENT ON TABLE recipes IS
    'Recetas. Nunca se borran: se archivan. current_version_id apunta a la última versión publicada.';

CREATE TABLE recipe_versions (
    id               bigint        NOT NULL DEFAULT nextval('recipe_versions_seq'),
    establishment_id bigint        NOT NULL,
    recipe_id        bigint        NOT NULL,
    version_number   integer       NOT NULL,
    status           varchar(20)   NOT NULL,
    base_rations     integer       NOT NULL,
    yield_pct        numeric(5,2)  NOT NULL DEFAULT 100,
    prep_time_minutes integer,
    instructions     text,
    total_cost       numeric(12,4),
    cost_per_ration  numeric(12,4),
    published_at     timestamptz,
    published_by     bigint,
    created_by       bigint        NOT NULL,
    created_at       timestamptz   NOT NULL DEFAULT now(),
    updated_at       timestamptz   NOT NULL DEFAULT now(),
    CONSTRAINT pk_recipe_versions PRIMARY KEY (id),
    CONSTRAINT fk_recipe_versions_establishment
        FOREIGN KEY (establishment_id) REFERENCES establishments (id),
    CONSTRAINT fk_recipe_versions_recipe FOREIGN KEY (recipe_id) REFERENCES recipes (id),
    CONSTRAINT fk_recipe_versions_created_by FOREIGN KEY (created_by) REFERENCES app_users (id),
    CONSTRAINT fk_recipe_versions_published_by FOREIGN KEY (published_by) REFERENCES app_users (id),
    CONSTRAINT uk_recipe_versions_number UNIQUE (recipe_id, version_number),
    CONSTRAINT ck_recipe_versions_status CHECK (status IN ('DRAFT', 'PUBLISHED', 'ARCHIVED')),
    CONSTRAINT ck_recipe_versions_base_rations CHECK (base_rations BETWEEN 1 AND 10000),
    -- REC-01: rendimiento tras cocción, informativo; no altera el coste de los ingredientes.
    CONSTRAINT ck_recipe_versions_yield CHECK (yield_pct > 0 AND yield_pct <= 100),
    CONSTRAINT ck_recipe_versions_prep_time
        CHECK (prep_time_minutes IS NULL OR prep_time_minutes BETWEEN 1 AND 1440),
    CONSTRAINT ck_recipe_versions_instructions
        CHECK (instructions IS NULL OR char_length(btrim(instructions)) BETWEEN 1 AND 4000),
    CONSTRAINT ck_recipe_versions_costs CHECK (
        (total_cost IS NULL OR total_cost >= 0)
        AND (cost_per_ration IS NULL OR cost_per_ration >= 0)),
    -- REC-04: publicar congela coste, fecha y autor; no puede quedar a medias.
    CONSTRAINT ck_recipe_versions_published CHECK (
        status <> 'PUBLISHED' OR (
            total_cost IS NOT NULL AND cost_per_ration IS NOT NULL
            AND published_at IS NOT NULL AND published_by IS NOT NULL)),
    -- Un borrador no tiene coste congelado ni datos de publicación.
    CONSTRAINT ck_recipe_versions_draft CHECK (
        status <> 'DRAFT' OR (
            total_cost IS NULL AND cost_per_ration IS NULL
            AND published_at IS NULL AND published_by IS NULL))
);

CREATE INDEX ix_recipe_versions_recipe ON recipe_versions (recipe_id, version_number DESC);

-- Un solo borrador por receta: la regla no depende de que la aplicación se acuerde.
CREATE UNIQUE INDEX uk_recipe_versions_single_draft ON recipe_versions (recipe_id) WHERE status = 'DRAFT';

COMMENT ON TABLE recipe_versions IS
    'Versiones de una receta. La publicada es inmutable y conserva el coste del momento (ECO-01).';

ALTER TABLE recipes
    ADD CONSTRAINT fk_recipes_current_version
    FOREIGN KEY (current_version_id) REFERENCES recipe_versions (id);

CREATE TABLE recipe_items (
    id               bigint        NOT NULL DEFAULT nextval('recipe_items_seq'),
    establishment_id bigint        NOT NULL,
    version_id       bigint        NOT NULL,
    product_id       bigint        NOT NULL,
    quantity_base    numeric(12,3) NOT NULL,
    waste_pct        numeric(5,2)  NOT NULL DEFAULT 0,
    sort_order       integer       NOT NULL,
    CONSTRAINT pk_recipe_items PRIMARY KEY (id),
    CONSTRAINT fk_recipe_items_establishment
        FOREIGN KEY (establishment_id) REFERENCES establishments (id),
    CONSTRAINT fk_recipe_items_version FOREIGN KEY (version_id) REFERENCES recipe_versions (id),
    CONSTRAINT fk_recipe_items_product FOREIGN KEY (product_id) REFERENCES products (id),
    CONSTRAINT uk_recipe_items_product UNIQUE (version_id, product_id),
    CONSTRAINT uk_recipe_items_order UNIQUE (version_id, sort_order),
    CONSTRAINT ck_recipe_items_quantity CHECK (quantity_base > 0),
    -- REC-02/ADR-033: la merma se suma a la cantidad neta; 100% vaciaría el ingrediente.
    CONSTRAINT ck_recipe_items_waste CHECK (waste_pct >= 0 AND waste_pct <= 99.99),
    CONSTRAINT ck_recipe_items_order CHECK (sort_order >= 0)
);

CREATE INDEX ix_recipe_items_version ON recipe_items (version_id, sort_order);
CREATE INDEX ix_recipe_items_product ON recipe_items (product_id);

COMMENT ON TABLE recipe_items IS
    'Ingredientes de una versión: cantidad POR RACIÓN en unidad base y merma porcentual.';

CREATE FUNCTION recipe_versions_immutable() RETURNS trigger
LANGUAGE plpgsql AS $$
BEGIN
    IF OLD.status <> 'DRAFT' THEN
        RAISE EXCEPTION 'recipe_versions es inmutable: una versión publicada no se modifica, se versiona'
            USING ERRCODE = 'integrity_constraint_violation';
    END IF;
    IF TG_OP = 'DELETE' THEN
        RETURN OLD;
    END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_recipe_versions_immutable
    BEFORE UPDATE OR DELETE ON recipe_versions
    FOR EACH ROW EXECUTE FUNCTION recipe_versions_immutable();

COMMENT ON FUNCTION recipe_versions_immutable() IS
    'Solo los borradores se editan o se borran; una versión publicada queda congelada.';

CREATE FUNCTION recipe_items_immutable() RETURNS trigger
LANGUAGE plpgsql AS $$
DECLARE
    parent_status varchar(20);
BEGIN
    -- Un ingrediente solo puede entrar, cambiar o salir mientras su versión es un borrador.
    IF TG_OP IN ('UPDATE', 'DELETE') THEN
        SELECT status INTO parent_status FROM recipe_versions WHERE id = OLD.version_id;
        IF parent_status IS NOT NULL AND parent_status <> 'DRAFT' THEN
            RAISE EXCEPTION 'Los ingredientes de una versión publicada no se modifican'
                USING ERRCODE = 'integrity_constraint_violation';
        END IF;
    END IF;
    IF TG_OP IN ('INSERT', 'UPDATE') THEN
        SELECT status INTO parent_status FROM recipe_versions WHERE id = NEW.version_id;
        IF parent_status IS NOT NULL AND parent_status <> 'DRAFT' THEN
            RAISE EXCEPTION 'Los ingredientes de una versión publicada no se modifican'
                USING ERRCODE = 'integrity_constraint_violation';
        END IF;
    END IF;
    IF TG_OP = 'DELETE' THEN
        RETURN OLD;
    END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_recipe_items_immutable
    BEFORE INSERT OR UPDATE OR DELETE ON recipe_items
    FOR EACH ROW EXECUTE FUNCTION recipe_items_immutable();

COMMENT ON FUNCTION recipe_items_immutable() IS
    'Los ingredientes solo cambian mientras su versión es un borrador.';
