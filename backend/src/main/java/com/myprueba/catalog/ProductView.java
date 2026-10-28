package com.myprueba.catalog;

import java.util.UUID;

/** A product as other modules see it. {@code costCents} is {@code null} when no cost was given. */
public record ProductView(UUID id, String name, long priceCents, Long costCents) {}
