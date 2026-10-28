package com.myprueba.customers.internal;

import java.util.UUID;

record CustomerRequest(String name, String phone, String note) {}

record CustomerResponse(UUID id, String name, String phone, String note) {}
