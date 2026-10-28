package com.myprueba.shared;

/** One invalid input field and a stable, machine-readable reason. */
public record FieldViolation(String field, String code) {}
