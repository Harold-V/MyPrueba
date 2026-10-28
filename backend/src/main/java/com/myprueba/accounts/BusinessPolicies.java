package com.myprueba.accounts;

import java.util.UUID;

/** Per-business rules other modules must respect. */
public interface BusinessPolicies {

    /** Whether a sale may leave a product with negative stock (default: yes). */
    boolean allowsNegativeStock(UUID businessId);
}
