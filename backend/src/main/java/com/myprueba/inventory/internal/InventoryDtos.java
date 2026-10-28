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

record TrackingRequest(Boolean tracked, String initialStock, String minStock) {}

record AdjustmentRequest(String type, String quantity, String note) {}

record InventoryItem(
        UUID productId,
        String name,
        String price,
        String cost,
        boolean tracked,
        String stock,
        String minStock,
        boolean low) {}

record InventorySummary(int tracked, int lowStock, String value) {}

record InventoryResponse(InventorySummary summary, List<InventoryItem> items) {}

record StockMovement(MovementType type, String change, String stockAfter, String note, Instant occurredAt) {}
