CREATE TABLE customers (
    id          VARCHAR(36)  NOT NULL PRIMARY KEY,
    business_id VARCHAR(36)  NOT NULL REFERENCES businesses (id),
    name        VARCHAR(80)  NOT NULL,
    name_key    VARCHAR(80)  NOT NULL,
    phone       VARCHAR(40),
    note        VARCHAR(500),
    created_at  TIMESTAMP    NOT NULL,
    updated_at  TIMESTAMP    NOT NULL
);

CREATE INDEX idx_customers_business ON customers (business_id, name_key);

ALTER TABLE sales ADD COLUMN customer_id VARCHAR(36) REFERENCES customers (id) ON DELETE SET NULL;
ALTER TABLE sales ADD COLUMN customer_name VARCHAR(80);

CREATE INDEX idx_sales_customer ON sales (customer_id);

CREATE TABLE customer_payments (
    id             VARCHAR(36)  NOT NULL PRIMARY KEY,
    business_id    VARCHAR(36)  NOT NULL REFERENCES businesses (id),
    customer_id    VARCHAR(36)  REFERENCES customers (id) ON DELETE SET NULL,
    customer_name  VARCHAR(80)  NOT NULL,
    amount_cents   BIGINT       NOT NULL,
    payment_method VARCHAR(20)  NOT NULL,
    occurred_at    TIMESTAMP    NOT NULL,
    note           VARCHAR(500),
    created_at     TIMESTAMP    NOT NULL,
    updated_at     TIMESTAMP    NOT NULL
);

CREATE INDEX idx_customer_payments_business_occurred ON customer_payments (business_id, occurred_at);
CREATE INDEX idx_customer_payments_customer ON customer_payments (customer_id);
