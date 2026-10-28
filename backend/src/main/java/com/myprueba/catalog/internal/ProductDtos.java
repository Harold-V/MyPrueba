package com.myprueba.catalog.internal;

import java.util.UUID;

record ProductRequest(String name, String price) {}

record ProductResponse(UUID id, String name, String price) {}
