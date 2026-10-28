package com.myprueba.accounts;

import java.util.UUID;

/** How a business presents itself on documents such as receipts. */
public interface BusinessProfiles {

    record BusinessProfile(String name, String document, String phone, String address) {}

    BusinessProfile profile(UUID businessId);
}
