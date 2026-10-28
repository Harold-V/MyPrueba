package com.myprueba.catalog;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProductCatalog {

    /** The product if it exists and belongs to {@code businessId}. */
    Optional<ProductView> find(UUID businessId, UUID productId);

    /** Every product of {@code businessId}, ordered by name. */
    List<ProductView> list(UUID businessId);
}
