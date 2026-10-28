package com.myprueba.accounts.internal;

import com.myprueba.accounts.BusinessPolicies;
import com.myprueba.shared.ValidationException;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

record BusinessSettings(Boolean allowNegativeStock) {}

@Component
class BusinessSettingsStore implements BusinessPolicies {

    private final JdbcClient jdbc;

    BusinessSettingsStore(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public boolean allowsNegativeStock(UUID businessId) {
        return jdbc.sql("SELECT allow_negative_stock FROM businesses WHERE id = ?")
                .param(businessId.toString())
                .query(Boolean.class)
                .optional()
                .orElse(true);
    }

    void setAllowNegativeStock(UUID businessId, boolean allow) {
        jdbc.sql("UPDATE businesses SET allow_negative_stock = ? WHERE id = ?")
                .params(allow, businessId.toString())
                .update();
    }
}

@RestController
class BusinessSettingsController {

    private final BusinessSettingsStore settings;

    BusinessSettingsController(BusinessSettingsStore settings) {
        this.settings = settings;
    }

    @GetMapping("/api/business/settings")
    BusinessSettings get(@AuthenticationPrincipal OwnerPrincipal owner) {
        return new BusinessSettings(settings.allowsNegativeStock(owner.businessId()));
    }

    @PutMapping(path = "/api/business/settings", consumes = MediaType.APPLICATION_JSON_VALUE)
    BusinessSettings update(@AuthenticationPrincipal OwnerPrincipal owner, @RequestBody BusinessSettings request) {
        if (request.allowNegativeStock() == null) {
            throw ValidationException.of("allowNegativeStock", "required");
        }
        settings.setAllowNegativeStock(owner.businessId(), request.allowNegativeStock());
        return get(owner);
    }
}
