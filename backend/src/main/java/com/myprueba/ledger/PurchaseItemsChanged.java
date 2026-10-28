package com.myprueba.ledger;

import java.util.Map;
import java.util.UUID;

/**
 * Published inside the expense's transaction whenever the product quantities bought in a purchase
 * change. {@code before} and {@code after} map product id to quantity in thousandths; lines without
 * a product are not included.
 */
public record PurchaseItemsChanged(
        UUID businessId, UUID expenseId, Kind kind, Map<UUID, Long> before, Map<UUID, Long> after) {

    public enum Kind {
        RECORDED,
        EDITED,
        DELETED
    }
}
