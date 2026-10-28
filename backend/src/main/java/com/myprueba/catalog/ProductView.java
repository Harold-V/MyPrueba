package com.myprueba.catalog;

import java.util.UUID;

public record ProductView(UUID id, String name, long priceCents) {}
