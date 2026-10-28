package com.myprueba.suppliers;

import java.util.UUID;

/**
 * Published inside the deletion's transaction, before the supplier is removed. A listener may veto
 * the deletion by throwing (the ledger does so while the business still owes the supplier money).
 */
public record SupplierDeleting(UUID businessId, UUID supplierId) {}
