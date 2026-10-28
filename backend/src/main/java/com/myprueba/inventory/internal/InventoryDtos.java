package com.myprueba.inventory.internal;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

enum MovementType {
    INITIAL,
    PURCHASE,
    LOSS,
    COUNT,
    SALE,
    SALE_EDIT,
    SALE_DELETE
}

enum AdjustmentType {
    PURCHASE,
    LOSS,
    COUNT
}

/** Only the minimum stock is configurable; every product always has stock. */
record StockSettingsRequest(String minStock) {}

record AdjustmentRequest(String type, String quantity, String note) {}

record InventoryItem(
        UUID productId, String name, String price, String cost, String stock, String minStock, boolean low) {}

/** {@code references}: products in the inventory; {@code value}: Σ stock × cost of products with cost. */
record InventorySummary(int references, int lowStock, String value) {}

record InventoryResponse(InventorySummary summary, List<InventoryItem> items) {}

record StockMovement(MovementType type, String change, String stockAfter, String note, Instant occurredAt) {}
