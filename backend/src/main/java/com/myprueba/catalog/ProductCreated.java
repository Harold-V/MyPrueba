package com.myprueba.catalog;

import java.util.UUID;

/** Published inside the creation's transaction: every product starts with stock (in thousandths). */
public record ProductCreated(UUID businessId, UUID productId, long initialStockMilli, Long minStockMilli) {}
