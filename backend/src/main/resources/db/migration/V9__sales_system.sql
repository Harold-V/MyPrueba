-- Sales system: business data for receipts, discounts, sale numbers, cash received, cash register.

ALTER TABLE businesses ADD COLUMN document VARCHAR(30);
ALTER TABLE businesses ADD COLUMN phone VARCHAR(40);
ALTER TABLE businesses ADD COLUMN address VARCHAR(200);

ALTER TABLE sales ADD COLUMN number BIGINT;
ALTER TABLE sales ADD COLUMN subtotal_cents BIGINT;
ALTER TABLE sales ADD COLUMN discount_cents BIGINT NOT NULL DEFAULT 0;
ALTER TABLE sales ADD COLUMN received_cents BIGINT;
ALTER TABLE sale_items ADD COLUMN discount_cents BIGINT NOT NULL DEFAULT 0;

-- Existing sales: no discounts, and numbers in the order they were recorded.
UPDATE sales SET subtotal_cents = total_cents;
UPDATE sales SET number = (
    SELECT COUNT(*) FROM sales s2
    WHERE s2.business_id = sales.business_id
      AND (s2.created_at < sales.created_at OR (s2.created_at = sales.created_at AND s2.id <= sales.id))
);

CREATE UNIQUE INDEX uq_sales_business_number ON sales (business_id, number);

CREATE TABLE cash_sessions (
    id               VARCHAR(36)  NOT NULL PRIMARY KEY,
    business_id      VARCHAR(36)  NOT NULL REFERENCES businesses (id),
    opened_at        TIMESTAMP    NOT NULL,
    opening_cents    BIGINT       NOT NULL,
    closed_at        TIMESTAMP,
    expected_cents   BIGINT,
    counted_cents    BIGINT,
    difference_cents BIGINT,
    note             VARCHAR(500)
);

CREATE INDEX idx_cash_sessions_business ON cash_sessions (business_id, opened_at);
