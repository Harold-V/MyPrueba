package com.myprueba.accounts.internal;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
class AuthController {

    private final AccountService accounts;
    private final SessionService sessions;
    private final SessionCookies cookies;

    AuthController(AccountService accounts, SessionService sessions, SessionCookies cookies) {
        this.accounts = accounts;
        this.sessions = sessions;
        this.cookies = cookies;
    }

    @PostMapping(path = "/api/auth/register", consumes = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<MeView> register(@RequestBody RegisterRequest request) {
        var session = accounts.register(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .header(HttpHeaders.SET_COOKIE, cookies.issue(session.token()).toString())
                .body(session.me());
    }

    @PostMapping(path = "/api/auth/login", consumes = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<MeView> login(@RequestBody LoginRequest request) {
        var session = accounts.login(request);
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookies.issue(session.token()).toString())
                .body(session.me());
    }

    @PostMapping("/api/auth/logout")
    ResponseEntity<Void> logout(@CookieValue(name = SessionCookies.NAME, required = false) String token) {
        sessions.close(token);
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, cookies.expire().toString())
                .build();
    }

    @GetMapping("/api/me")
    MeView me(@AuthenticationPrincipal OwnerPrincipal owner) {
        return accounts.me(owner.userId());
    }
}
