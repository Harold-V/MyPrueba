package com.myprueba.customers;

import java.util.UUID;

public record CustomerView(UUID id, String name, String phone, String document) {}
