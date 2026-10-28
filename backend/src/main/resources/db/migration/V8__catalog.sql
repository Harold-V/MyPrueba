-- Phase 4: categories, barcodes and products with variants.

CREATE TABLE product_categories (
    id          VARCHAR(36) NOT NULL PRIMARY KEY,
    business_id VARCHAR(36) NOT NULL REFERENCES businesses (id),
    name        VARCHAR(60) NOT NULL,
    name_key    VARCHAR(60) NOT NULL,
    created_at  TIMESTAMP   NOT NULL,
    updated_at  TIMESTAMP   NOT NULL,
    CONSTRAINT uq_product_categories_business_name UNIQUE (business_id, name_key)
);

CREATE TABLE product_groups (
    id          VARCHAR(36) NOT NULL PRIMARY KEY,
    business_id VARCHAR(36) NOT NULL REFERENCES businesses (id),
    name        VARCHAR(40) NOT NULL,
    category_id VARCHAR(36) REFERENCES product_categories (id) ON DELETE SET NULL,
    created_at  TIMESTAMP   NOT NULL,
    updated_at  TIMESTAMP   NOT NULL
);

CREATE INDEX idx_product_groups_business ON product_groups (business_id);

ALTER TABLE products ADD COLUMN category_id VARCHAR(36) REFERENCES product_categories (id) ON DELETE SET NULL;
ALTER TABLE products ADD COLUMN barcode VARCHAR(40);
ALTER TABLE products ADD COLUMN group_id VARCHAR(36) REFERENCES product_groups (id) ON DELETE CASCADE;
ALTER TABLE products ADD COLUMN variant VARCHAR(36);

CREATE UNIQUE INDEX uq_products_business_barcode ON products (business_id, barcode) WHERE barcode IS NOT NULL;
CREATE INDEX idx_products_category ON products (category_id);
CREATE INDEX idx_products_group ON products (group_id);
