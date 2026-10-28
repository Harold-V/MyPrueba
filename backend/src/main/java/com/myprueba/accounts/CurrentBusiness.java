package com.myprueba.accounts;

import java.time.ZoneId;
import java.util.UUID;

/** The business of the owner making the current, authenticated request. */
public interface CurrentBusiness {

    UUID id();

    ZoneId zone();
}
