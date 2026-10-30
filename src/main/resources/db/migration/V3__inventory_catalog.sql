-- V3 — Catálogo de inventario: categorías, productos, conversiones y proveedores (INV-01..03).
--
-- Convenciones de V1: snake_case plural, bigint con secuencia, instantes en UTC.
-- Todas las tablas de negocio llevan establishment_id (AGENTS.md §7.6), incluidas las conversiones.
--
-- Las cantidades son numeric(12,3) y los costes numeric(12,4) (AGENTS.md §7.1). El stock se guarda
-- siempre en la unidad base del producto: las conversiones se aplican en el borde.

CREATE SEQUENCE product_categories_seq START WITH 1 INCREMENT BY 1 NO CYCLE;
CREATE SEQUENCE products_seq START WITH 1 INCREMENT BY 1 NO CYCLE;
CREATE SEQUENCE product_unit_conversions_seq START WITH 1 INCREMENT BY 1 NO CYCLE;
CREATE SEQUENCE suppliers_seq START WITH 1 INCREMENT BY 1 NO CYCLE;

CREATE TABLE product_categories (
    id               bigint      NOT NULL DEFAULT nextval('product_categories_seq'),
    establishment_id bigint      NOT NULL,
    name             varchar(80) NOT NULL,
    created_at       timestamptz NOT NULL DEFAULT now(),
    updated_at       timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT pk_product_categories PRIMARY KEY (id),
    CONSTRAINT fk_product_categories_establishment
        FOREIGN KEY (establishment_id) REFERENCES establishments (id),
    CONSTRAINT uk_product_categories_name UNIQUE (establishment_id, name),
    CONSTRAINT ck_product_categories_name CHECK (name <> '')
);

COMMENT ON TABLE product_categories IS 'Categorías de producto (carnes, secos, bebidas...).';

CREATE TABLE products (
    id               bigint        NOT NULL DEFAULT nextval('products_seq'),
    establishment_id bigint        NOT NULL,
    category_id      bigint        NOT NULL,
    name             varchar(120)  NOT NULL,
    base_unit        varchar(10)   NOT NULL,
    min_stock        numeric(12,3) NOT NULL DEFAULT 0,
    current_stock    numeric(12,3) NOT NULL DEFAULT 0,
    current_avg_cost numeric(12,4) NOT NULL DEFAULT 0,
    allow_negative   boolean       NOT NULL DEFAULT false,
    active           boolean       NOT NULL DEFAULT true,
    created_at       timestamptz   NOT NULL DEFAULT now(),
    updated_at       timestamptz   NOT NULL DEFAULT now(),
    CONSTRAINT pk_products PRIMARY KEY (id),
    CONSTRAINT fk_products_establishment
        FOREIGN KEY (establishment_id) REFERENCES establishments (id),
    CONSTRAINT fk_products_category FOREIGN KEY (category_id) REFERENCES product_categories (id),
    CONSTRAINT uk_products_name UNIQUE (establishment_id, name),
    CONSTRAINT ck_products_name CHECK (name <> ''),
    CONSTRAINT ck_products_base_unit CHECK (base_unit IN ('G', 'ML', 'UNIT')),
    CONSTRAINT ck_products_min_stock CHECK (min_stock >= 0),
    CONSTRAINT ck_products_avg_cost CHECK (current_avg_cost >= 0),
    -- INV-08: con la política de bloqueo activa, la base de datos tampoco admite saldo negativo.
    CONSTRAINT ck_products_stock_policy CHECK (allow_negative OR current_stock >= 0)
);

CREATE INDEX ix_products_establishment ON products (establishment_id, active);
CREATE INDEX ix_products_category ON products (category_id);

COMMENT ON TABLE products IS 'Productos con unidad base y stock actual desnormalizado desde el kardex.';

CREATE TABLE product_unit_conversions (
    id               bigint         NOT NULL DEFAULT nextval('product_unit_conversions_seq'),
    establishment_id bigint         NOT NULL,
    product_id       bigint         NOT NULL,
    unit             varchar(20)    NOT NULL,
    factor_to_base   numeric(18,6)  NOT NULL,
    created_at       timestamptz    NOT NULL DEFAULT now(),
    CONSTRAINT pk_product_unit_conversions PRIMARY KEY (id),
    CONSTRAINT fk_product_unit_conversions_establishment
        FOREIGN KEY (establishment_id) REFERENCES establishments (id),
    CONSTRAINT fk_product_unit_conversions_product
        FOREIGN KEY (product_id) REFERENCES products (id),
    CONSTRAINT uk_product_unit_conversions_unit UNIQUE (product_id, unit),
    CONSTRAINT ck_product_unit_conversions_unit CHECK (unit = lower(unit) AND unit <> ''),
    CONSTRAINT ck_product_unit_conversions_factor CHECK (factor_to_base > 0)
);

CREATE INDEX ix_product_unit_conversions_product ON product_unit_conversions (product_id);

COMMENT ON TABLE product_unit_conversions IS
    'Factor de cada unidad de compra a la unidad base del producto (1 kg = 1000 g).';

CREATE TABLE suppliers (
    id               bigint       NOT NULL DEFAULT nextval('suppliers_seq'),
    establishment_id bigint       NOT NULL,
    name             varchar(120) NOT NULL,
    contact          varchar(200),
    active           boolean      NOT NULL DEFAULT true,
    created_at       timestamptz  NOT NULL DEFAULT now(),
    updated_at       timestamptz  NOT NULL DEFAULT now(),
    CONSTRAINT pk_suppliers PRIMARY KEY (id),
    CONSTRAINT fk_suppliers_establishment
        FOREIGN KEY (establishment_id) REFERENCES establishments (id),
    CONSTRAINT uk_suppliers_name UNIQUE (establishment_id, name),
    CONSTRAINT ck_suppliers_name CHECK (name <> '')
);

CREATE INDEX ix_suppliers_establishment ON suppliers (establishment_id, active);

COMMENT ON TABLE suppliers IS 'Proveedores. Nunca se borran: se desactivan.';
