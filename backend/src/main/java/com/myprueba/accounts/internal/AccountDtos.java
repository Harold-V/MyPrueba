package com.myprueba.accounts.internal;

import java.util.UUID;

record RegisterRequest(String email, String password, String businessName, String businessCategory) {}

record LoginRequest(String email, String password) {}

record BusinessRequest(String name, String category) {}

record UserView(UUID id, String email) {}

record BusinessView(UUID id, String name, BusinessCategory category, String currency, String timeZone) {}

record MeView(UserView user, BusinessView business) {}
