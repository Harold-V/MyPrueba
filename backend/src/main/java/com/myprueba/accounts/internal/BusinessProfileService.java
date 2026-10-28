package com.myprueba.accounts.internal;

import com.myprueba.accounts.BusinessProfiles;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
class BusinessProfileService implements BusinessProfiles {

    private final AccountRepository accounts;

    BusinessProfileService(AccountRepository accounts) {
        this.accounts = accounts;
    }

    @Override
    public BusinessProfile profile(UUID businessId) {
        BusinessView business = accounts.findBusiness(businessId).orElseThrow();
        return new BusinessProfile(business.name(), business.document(), business.phone(), business.address());
    }
}
