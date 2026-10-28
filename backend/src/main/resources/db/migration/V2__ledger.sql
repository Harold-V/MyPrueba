CREATE TABLE products (
    id          VARCHAR(36) NOT NULL PRIMARY KEY,
    business_id VARCHAR(36) NOT NULL REFERENCES businesses (id),
    name        VARCHAR(80) NOT NULL,
    name_key    VARCHAR(80) NOT NULL,
    price_cents BIGINT      NOT NULL,
    created_at  TIMESTAMP   NOT NULL,
    updated_at  TIMESTAMP   NOT NULL,
    CONSTRAINT uq_products_business_name UNIQUE (business_id, name_key)
);

CREATE TABLE sales (
    id             VARCHAR(36)  NOT NULL PRIMARY KEY,
    business_id    VARCHAR(36)  NOT NULL REFERENCES businesses (id),
    kind           VARCHAR(10)  NOT NULL,
    total_cents    BIGINT       NOT NULL,
    concept        VARCHAR(140),
    payment_method VARCHAR(20)  NOT NULL,
    occurred_at    TIMESTAMP    NOT NULL,
    note           VARCHAR(500),
    created_at     TIMESTAMP    NOT NULL,
    updated_at     TIMESTAMP    NOT NULL
);

CREATE INDEX idx_sales_business_occurred ON sales (business_id, occurred_at);

CREATE TABLE sale_items (
    id               VARCHAR(36) NOT NULL PRIMARY KEY,
    sale_id          VARCHAR(36) NOT NULL REFERENCES sales (id) ON DELETE CASCADE,
    product_id       VARCHAR(36) REFERENCES products (id) ON DELETE SET NULL,
    product_name     VARCHAR(80) NOT NULL,
    unit_price_cents BIGINT      NOT NULL,
    quantity_milli   BIGINT      NOT NULL,
    line_total_cents BIGINT      NOT NULL,
    position         INTEGER     NOT NULL
);

CREATE INDEX idx_sale_items_sale ON sale_items (sale_id);

CREATE TABLE expenses (
    id             VARCHAR(36)  NOT NULL PRIMARY KEY,
    business_id    VARCHAR(36)  NOT NULL REFERENCES businesses (id),
    amount_cents   BIGINT       NOT NULL,
    category       VARCHAR(20)  NOT NULL,
    concept        VARCHAR(140),
    payment_method VARCHAR(20)  NOT NULL,
    occurred_at    TIMESTAMP    NOT NULL,
    note           VARCHAR(500),
    created_at     TIMESTAMP    NOT NULL,
    updated_at     TIMESTAMP    NOT NULL
);

CREATE INDEX idx_expenses_business_occurred ON expenses (business_id, occurred_at);
