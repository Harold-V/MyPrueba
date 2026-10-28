package com.myprueba.suppliers.internal;

import java.util.UUID;

record SupplierRequest(String name, String phone, String document, String note) {}

record SupplierResponse(UUID id, String name, String phone, String document, String note) {}
