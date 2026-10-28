package com.myprueba.catalog.internal;

import com.myprueba.catalog.ProductView;
import com.myprueba.shared.Money;
import java.util.UUID;

/** A stored product with its category and, for a variant, its group. */
record ProductRow(
        UUID id,
        String name,
        long priceCents,
        Long costCents,
        UUID categoryId,
        String categoryName,
        String barcode,
        UUID groupId,
        String groupName,
        String variant) {

    ProductView view() {
        return new ProductView(id, name, priceCents, costCents, categoryId, barcode);
    }

    ProductResponse response() {
        return new ProductResponse(
                id,
                name,
                Money.format(priceCents),
                costCents == null ? null : Money.format(costCents),
                categoryId,
                categoryName,
                barcode,
                groupId,
                variant);
    }
}
