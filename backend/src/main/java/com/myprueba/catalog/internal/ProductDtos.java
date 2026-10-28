package com.myprueba.catalog.internal;

import java.util.List;
import java.util.UUID;

/**
 * {@code stock} and {@code minStock} are read only on creation; afterwards stock moves through
 * inventory. A variant is edited through {@code variant} (its name follows its product group).
 */
record ProductRequest(
        String name,
        String price,
        String cost,
        String stock,
        String minStock,
        String categoryId,
        String barcode,
        String variant) {}

record ProductResponse(
        UUID id,
        String name,
        String price,
        String cost,
        UUID categoryId,
        String categoryName,
        String barcode,
        UUID groupId,
        String variant) {}

record CategoryRequest(String name) {}

record CategoryResponse(UUID id, String name) {}

record VariantRequest(String name, String price, String cost, String stock, String minStock, String barcode) {}

record GroupRequest(String name, String categoryId, String price, String cost, List<VariantRequest> variants) {}

record GroupResponse(UUID id, String name, UUID categoryId, List<ProductResponse> variants) {}

record ImportResponse(int created) {}
