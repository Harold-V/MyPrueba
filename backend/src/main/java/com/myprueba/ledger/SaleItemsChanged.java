package com.myprueba.ledger;

import java.util.Map;
import java.util.UUID;

/**
 * Published inside the sale's transaction whenever the product quantities of a sale change.
 * {@code before} and {@code after} map product id to quantity in thousandths; lines without a
 * product are not included.
 */
public record SaleItemsChanged(UUID businessId, UUID saleId, Kind kind, Map<UUID, Long> before, Map<UUID, Long> after) {

    public enum Kind {
        RECORDED,
        EDITED,
        DELETED
    }
}
