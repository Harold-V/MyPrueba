package com.myprueba;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.forwardedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.myprueba.support.ApiTest;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;

/** The same origin serves the built frontend: real files, else the SPA shell for client routes. */
class SpaFallbackTest extends ApiTest {

    @Test
    void rootForwardsToTheShell() throws Exception {
        // Spring Boot's welcome page; MockMvc records the forward instead of following it.
        mvc.perform(get("/")).andExpect(status().isOk()).andExpect(forwardedUrl("index.html"));
    }

    @Test
    void clientRoutesServeTheShellWithoutASession() throws Exception {
        mvc.perform(get("/ventas/123/comprobante"))
                .andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString("spa-shell")));
    }

    @Test
    void missingFilesStayMissing() throws Exception {
        mvc.perform(get("/assets/missing.js")).andExpect(status().isNotFound());
    }

    @Test
    void apiStillRequiresASession() throws Exception {
        mvc.perform(get("/api/sales")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/does-not-exist")).andExpect(status().isUnauthorized());
    }

    @Test
    void unknownApiPathsAreNotTheShell() throws Exception {
        mvc.perform(get("/api/does-not-exist").cookie(registerOwner())).andExpect(status().isNotFound());
    }
}
