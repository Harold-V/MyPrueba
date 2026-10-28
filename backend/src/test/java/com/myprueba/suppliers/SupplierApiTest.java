package com.myprueba.suppliers;

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

class SupplierApiTest extends ApiTest {

    String create(Cookie sid, String body) throws Exception {
        return JsonPath.read(
                mvc.perform(json(post("/api/suppliers"), body).cookie(sid))
                        .andExpect(status().isCreated())
                        .andReturn()
                        .getResponse()
                        .getContentAsString(),
                "$.id");
    }

    @Test
    void ownerManagesSuppliers() throws Exception {
        Cookie sid = registerOwner();
        String postobon = create(sid, """
                {"name":" Distribuidora Andina ","phone":"601 555 0101","document":"900123456-7","note":"Visita los lunes"}
                """);
        create(sid, """
                {"name":"Alpina"}
                """);
        mvc.perform(get("/api/suppliers").cookie(sid))
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].name").value("Alpina"))
                .andExpect(jsonPath("$[1].name").value("Distribuidora Andina"))
                .andExpect(jsonPath("$[1].document").value("900123456-7"));
        mvc.perform(json(put("/api/suppliers/" + postobon), """
                        {"name":"Distribuidora Andina SAS","phone":"601 555 0101"}
                        """).cookie(sid))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Distribuidora Andina SAS"))
                .andExpect(jsonPath("$.note").doesNotExist());
        mvc.perform(delete("/api/suppliers/" + postobon).cookie(sid)).andExpect(status().isNoContent());
        mvc.perform(get("/api/suppliers").cookie(sid))
                .andExpect(jsonPath("$.length()").value(1));
        mvc.perform(json(post("/api/suppliers"), """
                        {"name":" "}
                        """).cookie(sid))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("name"));
    }

    @Test
    void anotherBusinessCannotSeeOrTouchSuppliers() throws Exception {
        Cookie a = registerOwner();
        Cookie b = registerOwner();
        String ofB = create(b, """
                {"name":"Proveedor de B"}
                """);
        mvc.perform(get("/api/suppliers").cookie(a))
                .andExpect(jsonPath("$.length()").value(0));
        mvc.perform(json(put("/api/suppliers/" + ofB), """
                        {"name":"X"}
                        """).cookie(a)).andExpect(status().isNotFound());
        mvc.perform(delete("/api/suppliers/" + ofB).cookie(a)).andExpect(status().isNotFound());
    }
}
