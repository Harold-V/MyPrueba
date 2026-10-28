package com.myprueba.customers;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.myprueba.support.ApiTest;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;

class CustomerApiTest extends ApiTest {

    String create(Cookie sid, String body) throws Exception {
        return JsonPath.read(
                mvc.perform(json(post("/api/customers"), body).cookie(sid))
                        .andExpect(status().isCreated())
                        .andReturn()
                        .getResponse()
                        .getContentAsString(),
                "$.id");
    }

    @Test
    void ownerManagesCustomers() throws Exception {
        Cookie sid = registerOwner();
        String juan = create(sid, """
                {"name":"  Juan Pérez ","phone":"300 123 4567","note":"Vecino del 3"}
                """);
        create(sid, """
                {"name":"Ana"}
                """);
        mvc.perform(get("/api/customers").cookie(sid))
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].name").value("Ana"))
                .andExpect(jsonPath("$[0].phone").doesNotExist())
                .andExpect(jsonPath("$[1].name").value("Juan Pérez"))
                .andExpect(jsonPath("$[1].phone").value("300 123 4567"));
        mvc.perform(json(put("/api/customers/" + juan), """
                        {"name":"Juan P.","phone":null}
                        """).cookie(sid))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Juan P."))
                .andExpect(jsonPath("$.note").doesNotExist());
        mvc.perform(delete("/api/customers/" + juan).cookie(sid)).andExpect(status().isNoContent());
        mvc.perform(get("/api/customers").cookie(sid))
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void nameIsRequired() throws Exception {
        mvc.perform(json(post("/api/customers"), """
                        {"name":" ","phone":"%s"}
                        """.formatted("9".repeat(41))).cookie(registerOwner()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[?(@.field=='name')].code").value("required"))
                .andExpect(jsonPath("$.errors[?(@.field=='phone')].code").value("too_long"));
    }

    @Test
    void anotherBusinessCannotSeeOrTouchCustomers() throws Exception {
        Cookie a = registerOwner();
        Cookie b = registerOwner();
        String ofB = create(b, """
                {"name":"Cliente de B"}
                """);
        mvc.perform(get("/api/customers").cookie(a))
                .andExpect(jsonPath("$.length()").value(0));
        mvc.perform(json(put("/api/customers/" + ofB), """
                        {"name":"Hackeado"}
                        """).cookie(a)).andExpect(status().isNotFound());
        mvc.perform(delete("/api/customers/" + ofB).cookie(a)).andExpect(status().isNotFound());
        mvc.perform(get("/api/customers").cookie(b))
                .andExpect(jsonPath("$[0].name").value("Cliente de B"));
    }
}
