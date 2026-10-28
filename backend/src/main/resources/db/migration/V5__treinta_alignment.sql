-- Phase 1: align with the behaviour verified with REA (02-DOCS/wiki/research/treinta-har-2026-10-28.md).

ALTER TABLE sales ADD COLUMN due_date DATE;
ALTER TABLE businesses ADD COLUMN allow_negative_stock BOOLEAN NOT NULL DEFAULT TRUE;

UPDATE expenses SET category = 'OTHER' WHERE category = 'TAXES';

UPDATE businesses SET category = 'LOCAL_MARKET' WHERE category = 'GROCERY';
UPDATE businesses SET category = 'RESTAURANT' WHERE category = 'FOOD';
UPDATE businesses SET category = 'PROFESSIONAL_SERVICES' WHERE category = 'SERVICES';
UPDATE businesses SET category = NULL WHERE category = 'OTHER';

-- Every product has stock from now on.
UPDATE stock_items SET tracked = TRUE;
INSERT INTO stock_items (product_id, business_id, tracked, quantity_milli, min_milli, updated_at)
SELECT p.id, p.business_id, TRUE, 0, NULL, p.updated_at
FROM products p
WHERE NOT EXISTS (SELECT 1 FROM stock_items s WHERE s.product_id = p.id);
