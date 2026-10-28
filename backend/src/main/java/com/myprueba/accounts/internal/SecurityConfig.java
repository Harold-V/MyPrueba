package com.myprueba.accounts.internal;

import com.myprueba.shared.ProblemResponses;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;
import tools.jackson.databind.json.JsonMapper;

/**
 * CSRF protection comes from the {@code SameSite=Strict} session cookie plus JSON-only write
 * endpoints (a cross-site form cannot send JSON without a CORS preflight, and CORS stays closed).
 */
@Configuration(proxyBeanMethods = false)
class SecurityConfig {

    @Bean
    PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }

    @Bean
    SecurityFilterChain apiSecurity(HttpSecurity http, SessionService sessions, JsonMapper json) throws Exception {
        http.csrf(csrf -> csrf.disable())
                .httpBasic(basic -> basic.disable())
                .formLogin(form -> form.disable())
                .logout(logout -> logout.disable())
                .requestCache(cache -> cache.disable())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .addFilterBefore(new SessionAuthenticationFilter(sessions), AnonymousAuthenticationFilter.class)
                .authorizeHttpRequests(auth -> auth.requestMatchers(
                                HttpMethod.POST, "/api/auth/register", "/api/auth/login", "/api/auth/logout")
                        .permitAll()
                        .requestMatchers("/actuator/health", "/v3/api-docs", "/v3/api-docs/**", "/error")
                        .permitAll()
                        .requestMatchers("/api/**")
                        .authenticated()
                        // The built frontend (files and client routes) is public; its data is not.
                        .anyRequest()
                        .permitAll())
                .exceptionHandling(ex -> ex.authenticationEntryPoint((request, response, e) -> {
                    response.setStatus(HttpStatus.UNAUTHORIZED.value());
                    response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
                    json.writeValue(
                            response.getOutputStream(),
                            ProblemResponses.problem(HttpStatus.UNAUTHORIZED, "unauthenticated", List.of()));
                }));
        return http.build();
    }
}
