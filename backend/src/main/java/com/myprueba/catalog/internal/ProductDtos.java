package com.myprueba.catalog.internal;

import java.util.UUID;

/** {@code stock} and {@code minStock} are read only on creation; afterwards stock moves through inventory. */
record ProductRequest(String name, String price, String cost, String stock, String minStock) {}

record ProductResponse(UUID id, String name, String price, String cost) {}
