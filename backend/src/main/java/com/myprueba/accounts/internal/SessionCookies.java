package com.myprueba.accounts.internal;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

@Component
class SessionCookies {

    static final String NAME = "sid";

    private final boolean secure;

    SessionCookies(@Value("${app.session.cookie-secure}") boolean secure) {
        this.secure = secure;
    }

    ResponseCookie issue(String token) {
        return base(token).maxAge(SessionService.IDLE_TIMEOUT).build();
    }

    ResponseCookie expire() {
        return base("").maxAge(0).build();
    }

    private ResponseCookie.ResponseCookieBuilder base(String value) {
        return ResponseCookie.from(NAME, value)
                .httpOnly(true)
                .secure(secure)
                .sameSite("Strict")
                .path("/api");
    }
}
