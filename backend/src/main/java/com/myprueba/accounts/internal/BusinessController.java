package com.myprueba.accounts.internal;

import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
class BusinessController {

    private final AccountService accounts;

    BusinessController(AccountService accounts) {
        this.accounts = accounts;
    }

    @PutMapping(path = "/api/business", consumes = MediaType.APPLICATION_JSON_VALUE)
    BusinessView update(@AuthenticationPrincipal OwnerPrincipal owner, @RequestBody BusinessRequest request) {
        return accounts.updateBusiness(owner, request);
    }
}
