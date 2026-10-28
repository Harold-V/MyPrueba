package com.myprueba.support;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.servlet.http.Cookie;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** Base for HTTP-level tests against a real SQLite database. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestClockConfig.class)
public abstract class ApiTest {

    protected static final String PASSWORD = "secreto123";

    @Autowired
    protected MockMvc mvc;

    @Autowired
    protected MutableClock clock;

    protected static String uniqueEmail() {
        return "owner-" + UUID.randomUUID() + "@example.com";
    }

    /** Registers a fresh owner and returns their session cookie. */
    protected Cookie registerOwner() throws Exception {
        return registerOwner(uniqueEmail(), "Tienda Prueba");
    }

    protected Cookie registerOwner(String email, String businessName) throws Exception {
        var result = mvc.perform(json(post("/api/auth/register"), """
                        {"email":"%s","password":"%s","businessName":"%s"}
                        """.formatted(email, PASSWORD, businessName)))
                .andExpect(status().isCreated())
                .andReturn();
        return result.getResponse().getCookie("sid");
    }

    protected static MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder builder, String body) {
        return builder.contentType(MediaType.APPLICATION_JSON).content(body);
    }
}
