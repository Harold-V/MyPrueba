-- Phase 3: suppliers, purchases with product lines, and accounts payable.

CREATE TABLE suppliers (
    id          VARCHAR(36)  NOT NULL PRIMARY KEY,
    business_id VARCHAR(36)  NOT NULL REFERENCES businesses (id),
    name        VARCHAR(80)  NOT NULL,
    name_key    VARCHAR(80)  NOT NULL,
    phone       VARCHAR(40),
    document    VARCHAR(30),
    note        VARCHAR(500),
    created_at  TIMESTAMP    NOT NULL,
    updated_at  TIMESTAMP    NOT NULL
);

CREATE INDEX idx_suppliers_business ON suppliers (business_id, name_key);

ALTER TABLE expenses ADD COLUMN supplier_id VARCHAR(36) REFERENCES suppliers (id) ON DELETE SET NULL;
ALTER TABLE expenses ADD COLUMN supplier_name VARCHAR(80);
ALTER TABLE expenses ADD COLUMN due_date DATE;

CREATE INDEX idx_expenses_supplier ON expenses (supplier_id);

CREATE TABLE expense_items (
    id               VARCHAR(36) NOT NULL PRIMARY KEY,
    expense_id       VARCHAR(36) NOT NULL REFERENCES expenses (id) ON DELETE CASCADE,
    product_id       VARCHAR(36) REFERENCES products (id) ON DELETE SET NULL,
    product_name     VARCHAR(80) NOT NULL,
    unit_cost_cents  BIGINT      NOT NULL,
    quantity_milli   BIGINT      NOT NULL,
    line_total_cents BIGINT      NOT NULL,
    position         INTEGER     NOT NULL
);

CREATE INDEX idx_expense_items_expense ON expense_items (expense_id);

CREATE TABLE supplier_payments (
    id             VARCHAR(36)  NOT NULL PRIMARY KEY,
    business_id    VARCHAR(36)  NOT NULL REFERENCES businesses (id),
    supplier_id    VARCHAR(36)  REFERENCES suppliers (id) ON DELETE SET NULL,
    supplier_name  VARCHAR(80)  NOT NULL,
    amount_cents   BIGINT       NOT NULL,
    payment_method VARCHAR(20)  NOT NULL,
    occurred_at    TIMESTAMP    NOT NULL,
    note           VARCHAR(500),
    created_at     TIMESTAMP    NOT NULL,
    updated_at     TIMESTAMP    NOT NULL
);

CREATE INDEX idx_supplier_payments_business_occurred ON supplier_payments (business_id, occurred_at);
CREATE INDEX idx_supplier_payments_supplier ON supplier_payments (supplier_id);
