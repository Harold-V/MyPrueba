package com.myprueba.accounts.internal;

import com.myprueba.accounts.CurrentBusiness;
import java.time.ZoneId;
import java.util.UUID;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component
class CurrentBusinessFromSession implements CurrentBusiness {

    @Override
    public UUID id() {
        return owner().businessId();
    }

    @Override
    public ZoneId zone() {
        return owner().zone();
    }

    private static OwnerPrincipal owner() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof OwnerPrincipal owner) {
            return owner;
        }
        throw new IllegalStateException("No authenticated owner in the current request");
    }
}
