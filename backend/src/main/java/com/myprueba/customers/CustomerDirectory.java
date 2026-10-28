package com.myprueba.customers;

import java.util.Optional;
import java.util.UUID;

public interface CustomerDirectory {

    /** The customer if it exists and belongs to {@code businessId}. */
    Optional<CustomerView> find(UUID businessId, UUID customerId);
}
