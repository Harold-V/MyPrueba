package com.myprueba.customers;

import java.util.UUID;

/**
 * Published inside the deletion's transaction, before the customer is removed. A listener may veto
 * the deletion by throwing (the ledger does so while the customer still owes money).
 */
public record CustomerDeleting(UUID businessId, UUID customerId) {}
