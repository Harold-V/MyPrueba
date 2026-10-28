package com.myprueba.catalog;

import java.util.UUID;

/**
 * A product as other modules see it. {@code costCents}, {@code categoryId} and {@code barcode} are
 * {@code null} when not given. A variant of a product is a product of its own.
 */
public record ProductView(UUID id, String name, long priceCents, Long costCents, UUID categoryId, String barcode) {}
