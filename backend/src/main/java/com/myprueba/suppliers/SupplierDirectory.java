package com.myprueba.suppliers;

import java.util.Optional;
import java.util.UUID;

public interface SupplierDirectory {

    /** The supplier if it exists and belongs to {@code businessId}. */
    Optional<SupplierView> find(UUID businessId, UUID supplierId);
}
