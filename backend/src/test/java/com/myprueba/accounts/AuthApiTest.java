package com.myprueba.accounts;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.myprueba.support.ApiTest;
import jakarta.servlet.http.Cookie;
import java.time.Duration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;

class AuthApiTest extends ApiTest {

    @Autowired
    JdbcClient jdbc;

    @AfterEach
    void resetClock() {
        clock.reset();
    }

    @Test
    void registerCreatesOwnerBusinessAndSession() throws Exception {
        String email = uniqueEmail();
        mvc.perform(json(post("/api/auth/register"), """
                        {"email":"  %s ","password":"secreto123","businessName":"  Tienda Doña Rosa ","businessCategory":"GROCERY"}
                        """.formatted(email.toUpperCase())))
                .andExpect(status().isCreated())
                .andExpect(cookie().exists("sid"))
                .andExpect(cookie().httpOnly("sid", true))
                .andExpect(jsonPath("$.user.email").value(email))
                .andExpect(jsonPath("$.business.name").value("Tienda Doña Rosa"))
                .andExpect(jsonPath("$.business.category").value("GROCERY"))
                .andExpect(jsonPath("$.business.currency").value("COP"))
                .andExpect(jsonPath("$.business.timeZone").value("America/Bogota"));
    }

    @Test
    void registeredSessionGivesAccessToMe() throws Exception {
        String email = uniqueEmail();
        Cookie sid = registerOwner(email, "Mi Tienda");
        mvc.perform(get("/api/me").cookie(sid))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.email").value(email))
                .andExpect(jsonPath("$.business.name").value("Mi Tienda"))
                .andExpect(jsonPath("$.business.category").doesNotExist());
    }

    @Test
    void duplicateEmailInAnyCaseIsRejected() throws Exception {
        String email = uniqueEmail();
        registerOwner(email, "Uno");
        mvc.perform(json(post("/api/auth/register"), """
                        {"email":"%s","password":"secreto123","businessName":"Dos"}
                        """.formatted(email.toUpperCase())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("email_taken"));
    }

    @Test
    void shortPasswordAndBlankBusinessNameAreReportedTogether() throws Exception {
        mvc.perform(json(post("/api/auth/register"), """
                        {"email":"no-es-email","password":"1234567","businessName":"   ","businessCategory":"SPACESHIPS"}
                        """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("validation_failed"))
                .andExpect(jsonPath("$.errors[?(@.field=='email')].code").value("invalid_format"))
                .andExpect(jsonPath("$.errors[?(@.field=='password')].code").value("too_short"))
                .andExpect(jsonPath("$.errors[?(@.field=='businessName')].code").value("required"))
                .andExpect(jsonPath("$.errors[?(@.field=='businessCategory')].code")
                        .value("invalid_value"));
    }

    @Test
    void passwordIsStoredHashed() throws Exception {
        String email = uniqueEmail();
        registerOwner(email, "Hash");
        String hash = jdbc.sql("SELECT password_hash FROM users WHERE email = ?")
                .param(email)
                .query(String.class)
                .single();
        assertThat(hash).isNotEqualTo(PASSWORD).startsWith("{bcrypt}");
    }

    @Test
    void loginWithValidCredentialsOpensSession() throws Exception {
        String email = uniqueEmail();
        registerOwner(email, "Login");
        var login = mvc.perform(json(post("/api/auth/login"), """
                        {"email":"%s","password":"%s"}
                        """.formatted(email.toUpperCase(), PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.business.name").value("Login"))
                .andReturn();
        Cookie sid = login.getResponse().getCookie("sid");
        mvc.perform(get("/api/me").cookie(sid)).andExpect(status().isOk());
    }

    @Test
    void unknownEmailAndWrongPasswordGiveIdenticalErrors() throws Exception {
        String email = uniqueEmail();
        registerOwner(email, "Iguales");
        String wrongPassword = mvc.perform(json(post("/api/auth/login"), """
                        {"email":"%s","password":"incorrecta"}
                        """.formatted(email)))
                .andExpect(status().isUnauthorized())
                .andReturn()
                .getResponse()
                .getContentAsString();
        String unknownEmail = mvc.perform(json(post("/api/auth/login"), """
                        {"email":"%s","password":"incorrecta"}
                        """.formatted(uniqueEmail())))
                .andExpect(status().isUnauthorized())
                .andReturn()
                .getResponse()
                .getContentAsString();
        assertThat(wrongPassword).isEqualTo(unknownEmail).contains("invalid_credentials");
    }

    @Test
    void overlongPasswordsAreRejectedNotCrashing() throws Exception {
        String longPassword = "a".repeat(100);
        mvc.perform(json(post("/api/auth/register"), """
                        {"email":"%s","password":"%s","businessName":"X"}
                        """.formatted(uniqueEmail(), longPassword)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[?(@.field=='password')].code").value("too_long"));
        String email = uniqueEmail();
        registerOwner(email, "Largo");
        mvc.perform(json(post("/api/auth/login"), """
                        {"email":"%s","password":"%s"}
                        """.formatted(email, longPassword)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("invalid_credentials"));
        mvc.perform(json(post("/api/auth/login"), "{}")).andExpect(status().isUnauthorized());
        mvc.perform(json(post("/api/auth/login"), "{no es json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("malformed_request"));
    }

    @Test
    void logoutInvalidatesTheSession() throws Exception {
        Cookie sid = registerOwner();
        mvc.perform(post("/api/auth/logout").cookie(sid))
                .andExpect(status().isNoContent())
                .andExpect(cookie().maxAge("sid", 0));
        mvc.perform(get("/api/me").cookie(sid))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("unauthenticated"));
    }

    @Test
    void requestsWithoutSessionAreRejected() throws Exception {
        mvc.perform(get("/api/me")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/me").cookie(new Cookie("sid", "inventado"))).andExpect(status().isUnauthorized());
    }

    @Test
    void sessionExpiresAfterSevenDaysWithoutUse() throws Exception {
        Cookie sid = registerOwner();
        clock.set(clock.instant().plus(Duration.ofDays(6)));
        mvc.perform(get("/api/me").cookie(sid)).andExpect(status().isOk());
        clock.set(clock.instant().plus(Duration.ofDays(6)));
        mvc.perform(get("/api/me").cookie(sid)).andExpect(status().isOk());
        clock.set(clock.instant().plus(Duration.ofDays(8)));
        mvc.perform(get("/api/me").cookie(sid)).andExpect(status().isUnauthorized());
    }

    @Test
    void ownerRenamesBusiness() throws Exception {
        Cookie sid = registerOwner();
        mvc.perform(json(put("/api/business"), """
                        {"name":"Nuevo Nombre","category":"BEAUTY"}
                        """).cookie(sid))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Nuevo Nombre"))
                .andExpect(jsonPath("$.category").value("BEAUTY"));
        mvc.perform(get("/api/me").cookie(sid))
                .andExpect(jsonPath("$.business.name").value("Nuevo Nombre"));
    }

    @Test
    void renamingOnlyAffectsOwnBusiness() throws Exception {
        Cookie a = registerOwner(uniqueEmail(), "Negocio A");
        Cookie b = registerOwner(uniqueEmail(), "Negocio B");
        mvc.perform(json(put("/api/business"), """
                        {"name":"Cambiado por A"}
                        """).cookie(a)).andExpect(status().isOk());
        mvc.perform(get("/api/me").cookie(b))
                .andExpect(jsonPath("$.business.name").value("Negocio B"));
    }

    @Test
    void formEncodedWritesAreRefused() throws Exception {
        mvc.perform(post("/api/auth/login")
                        .contentType("application/x-www-form-urlencoded")
                        .content("email=a%40b.co&password=x"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.code").value("unsupported_media_type"));
    }
}
