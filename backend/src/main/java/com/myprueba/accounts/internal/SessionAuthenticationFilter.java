package com.myprueba.accounts.internal;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/** Turns a valid {@code sid} cookie into an authenticated {@link OwnerPrincipal}. */
class SessionAuthenticationFilter extends OncePerRequestFilter {

    private final SessionService sessions;

    SessionAuthenticationFilter(SessionService sessions) {
        this.sessions = sessions;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Cookie[] cookies = request.getCookies();
        if (cookies != null) {
            Arrays.stream(cookies)
                    .filter(c -> SessionCookies.NAME.equals(c.getName()))
                    .findFirst()
                    .flatMap(c -> sessions.authenticate(c.getValue()))
                    .ifPresent(owner -> {
                        var context = SecurityContextHolder.createEmptyContext();
                        context.setAuthentication(
                                UsernamePasswordAuthenticationToken.authenticated(owner, null, List.of()));
                        SecurityContextHolder.setContext(context);
                    });
        }
        chain.doFilter(request, response);
    }
}
