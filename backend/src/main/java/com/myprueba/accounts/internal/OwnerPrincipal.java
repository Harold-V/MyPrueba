package com.myprueba.accounts.internal;

import java.time.ZoneId;
import java.util.UUID;

record OwnerPrincipal(UUID userId, UUID businessId, ZoneId zone) {}
