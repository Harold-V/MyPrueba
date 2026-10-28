ALTER TABLE products ADD COLUMN cost_cents BIGINT;

CREATE TABLE stock_items (
    product_id     VARCHAR(36) NOT NULL PRIMARY KEY REFERENCES products (id) ON DELETE CASCADE,
    business_id    VARCHAR(36) NOT NULL REFERENCES businesses (id),
    tracked        BOOLEAN     NOT NULL,
    quantity_milli BIGINT      NOT NULL,
    min_milli      BIGINT,
    updated_at     TIMESTAMP   NOT NULL
);

CREATE INDEX idx_stock_items_business ON stock_items (business_id);

CREATE TABLE stock_movements (
    id                VARCHAR(36)  NOT NULL PRIMARY KEY,
    product_id        VARCHAR(36)  NOT NULL REFERENCES products (id) ON DELETE CASCADE,
    business_id       VARCHAR(36)  NOT NULL REFERENCES businesses (id),
    seq               BIGINT       NOT NULL,
    type              VARCHAR(20)  NOT NULL,
    change_milli      BIGINT       NOT NULL,
    stock_after_milli BIGINT       NOT NULL,
    note              VARCHAR(500),
    sale_id           VARCHAR(36),
    occurred_at       TIMESTAMP    NOT NULL,
    CONSTRAINT uq_stock_movements_seq UNIQUE (product_id, seq)
);
