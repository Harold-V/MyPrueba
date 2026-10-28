package com.myprueba.catalog;

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

class ProductApiTest extends ApiTest {

    String create(Cookie sid, String name, String price) throws Exception {
        String body = mvc.perform(
                        json(post("/api/products"), """
                        {"name":"%s","price":"%s"}
                        """.formatted(name, price)).cookie(sid))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return JsonPath.read(body, "$.id");
    }

    @Test
    void ownerCreatesListsUpdatesAndDeletesProducts() throws Exception {
        Cookie sid = registerOwner();
        String pan = create(sid, "Pan", "500");
        create(sid, "Gaseosa", "3000.5");

        mvc.perform(get("/api/products").cookie(sid))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].name").value("Gaseosa"))
                .andExpect(jsonPath("$[0].price").value("3000.50"))
                .andExpect(jsonPath("$[1].name").value("Pan"));

        mvc.perform(json(put("/api/products/" + pan), """
                        {"name":"Pan francés","price":"600"}
                        """).cookie(sid))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Pan francés"))
                .andExpect(jsonPath("$.price").value("600.00"));

        mvc.perform(delete("/api/products/" + pan).cookie(sid)).andExpect(status().isNoContent());
        mvc.perform(get("/api/products").cookie(sid))
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void productNamesAreUniquePerBusinessIgnoringCase() throws Exception {
        Cookie sid = registerOwner();
        create(sid, "Pan", "500");
        mvc.perform(json(post("/api/products"), """
                        {"name":"  pan ","price":"700"}
                        """).cookie(sid))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("product_name_taken"));
        create(registerOwner(), "Pan", "500");
    }

    @Test
    void invalidProductIsRejectedWithAllFields() throws Exception {
        mvc.perform(json(post("/api/products"), """
                        {"name":" ","price":"0"}
                        """).cookie(registerOwner()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[?(@.field=='name')].code").value("required"))
                .andExpect(jsonPath("$.errors[?(@.field=='price')].code").value("must_be_positive"));
    }

    @Test
    void anotherBusinessCannotSeeOrTouchProducts() throws Exception {
        Cookie a = registerOwner();
        Cookie b = registerOwner();
        String productOfB = create(b, "Secreto", "100");

        mvc.perform(get("/api/products").cookie(a))
                .andExpect(jsonPath("$.length()").value(0));
        String unknown = mvc.perform(
                        json(put("/api/products/" + productOfB), """
                        {"name":"Hackeado","price":"1"}
                        """).cookie(a))
                .andExpect(status().isNotFound())
                .andReturn()
                .getResponse()
                .getContentAsString();
        mvc.perform(delete("/api/products/" + productOfB).cookie(a)).andExpect(status().isNotFound());
        String missing = mvc.perform(delete("/api/products/00000000-0000-0000-0000-000000000000")
                        .cookie(a))
                .andExpect(status().isNotFound())
                .andReturn()
                .getResponse()
                .getContentAsString();
        org.assertj.core.api.Assertions.assertThat(unknown.replace(productOfB, "ID"))
                .isEqualTo(missing.replace("00000000-0000-0000-0000-000000000000", "ID"));
        mvc.perform(get("/api/products").cookie(b))
                .andExpect(jsonPath("$[0].name").value("Secreto"));
    }

    @Test
    void malformedIdLooksLikeMissing() throws Exception {
        mvc.perform(delete("/api/products/no-es-uuid").cookie(registerOwner())).andExpect(status().isNotFound());
    }
}
