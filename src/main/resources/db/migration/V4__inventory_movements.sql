-- V4 — Lotes y movimientos de stock: kardex inmutable (INV-04..09, CAL-01, CAL-04).
--
-- El stock sigue viviendo en products.current_stock (desnormalización de rendimiento) y su fuente de
-- verdad pasa a ser stock_movements. Las cantidades son numeric(12,3) y los costes numeric(12,4),
-- siempre en la unidad base del producto (AGENTS.md §7).
--
-- stock_movements no se edita ni se borra: además de no existir endpoint, un trigger lo impide en la
-- propia base de datos. Las correcciones se hacen con un movimiento de ajuste (regla 7.5).

CREATE SEQUENCE stock_lots_seq START WITH 1 INCREMENT BY 1 NO CYCLE;
CREATE SEQUENCE stock_movements_seq START WITH 1 INCREMENT BY 1 NO CYCLE;

CREATE TABLE stock_lots (
    id               bigint        NOT NULL DEFAULT nextval('stock_lots_seq'),
    establishment_id bigint        NOT NULL,
    product_id       bigint        NOT NULL,
    supplier_id      bigint,
    received_at      timestamptz   NOT NULL,
    expires_at       timestamptz,
    unit_cost        numeric(12,4) NOT NULL,
    initial_qty      numeric(12,3) NOT NULL,
    remaining_qty    numeric(12,3) NOT NULL,
    created_at       timestamptz   NOT NULL DEFAULT now(),
    CONSTRAINT pk_stock_lots PRIMARY KEY (id),
    CONSTRAINT fk_stock_lots_establishment
        FOREIGN KEY (establishment_id) REFERENCES establishments (id),
    CONSTRAINT fk_stock_lots_product FOREIGN KEY (product_id) REFERENCES products (id),
    CONSTRAINT fk_stock_lots_supplier FOREIGN KEY (supplier_id) REFERENCES suppliers (id),
    CONSTRAINT ck_stock_lots_unit_cost CHECK (unit_cost >= 0),
    CONSTRAINT ck_stock_lots_initial_qty CHECK (initial_qty > 0),
    CONSTRAINT ck_stock_lots_remaining_qty CHECK (remaining_qty >= 0 AND remaining_qty <= initial_qty),
    -- ADR-025/D14: la caducidad es opcional, pero si existe debe ser posterior a la recepción.
    CONSTRAINT ck_stock_lots_expires CHECK (expires_at IS NULL OR expires_at > received_at)
);

-- FIFO por caducidad (los lotes sin fecha van al final) y consulta de alertas.
CREATE INDEX ix_stock_lots_product ON stock_lots (product_id, expires_at, received_at);
CREATE INDEX ix_stock_lots_supplier ON stock_lots (supplier_id);

COMMENT ON TABLE stock_lots IS
    'Lotes de entrada. remaining_qty es el saldo físico; la suma por producto cuadra con el stock.';

CREATE TABLE stock_movements (
    id               bigint        NOT NULL DEFAULT nextval('stock_movements_seq'),
    establishment_id bigint        NOT NULL,
    product_id       bigint        NOT NULL,
    lot_id           bigint,
    type             varchar(30)   NOT NULL,
    quantity_base    numeric(12,3) NOT NULL,
    unit_cost        numeric(12,4) NOT NULL,
    balance_after    numeric(12,3) NOT NULL,
    reason           varchar(200),
    reference_type   varchar(40),
    reference_id     bigint,
    actor_id         bigint        NOT NULL,
    occurred_at      timestamptz   NOT NULL,
    created_at       timestamptz   NOT NULL DEFAULT now(),
    CONSTRAINT pk_stock_movements PRIMARY KEY (id),
    CONSTRAINT fk_stock_movements_establishment
        FOREIGN KEY (establishment_id) REFERENCES establishments (id),
    CONSTRAINT fk_stock_movements_product FOREIGN KEY (product_id) REFERENCES products (id),
    CONSTRAINT fk_stock_movements_lot FOREIGN KEY (lot_id) REFERENCES stock_lots (id),
    CONSTRAINT fk_stock_movements_actor FOREIGN KEY (actor_id) REFERENCES app_users (id),
    CONSTRAINT ck_stock_movements_quantity CHECK (quantity_base > 0),
    CONSTRAINT ck_stock_movements_unit_cost CHECK (unit_cost >= 0),
    -- ADR-026/D10: el motivo es libre pero acotado; las entradas de compra no lo llevan.
    CONSTRAINT ck_stock_movements_reason
        CHECK (reason IS NULL OR char_length(btrim(reason)) BETWEEN 5 AND 200),
    CONSTRAINT ck_stock_movements_type CHECK (type IN (
        'PURCHASE_IN', 'PRODUCTION_OUT', 'WASTE_OUT', 'ADJUSTMENT_IN', 'ADJUSTMENT_OUT'))
);

CREATE INDEX ix_stock_movements_product ON stock_movements (product_id, occurred_at DESC, id DESC);
CREATE INDEX ix_stock_movements_lot ON stock_movements (lot_id);
CREATE INDEX ix_stock_movements_actor ON stock_movements (actor_id);

COMMENT ON TABLE stock_movements IS
    'Kardex append-only: cada fila es un hecho con su saldo resultante y el coste aplicado.';

CREATE FUNCTION stock_movements_immutable() RETURNS trigger
LANGUAGE plpgsql AS $$
BEGIN
    RAISE EXCEPTION 'stock_movements es un libro inmutable: usa un movimiento de ajuste'
        USING ERRCODE = 'integrity_constraint_violation';
END;
$$;

CREATE TRIGGER trg_stock_movements_immutable
    BEFORE UPDATE OR DELETE ON stock_movements
    FOR EACH ROW EXECUTE FUNCTION stock_movements_immutable();

COMMENT ON FUNCTION stock_movements_immutable() IS
    'Bloquea UPDATE y DELETE sobre stock_movements incluso para el propietario de la tabla.';
