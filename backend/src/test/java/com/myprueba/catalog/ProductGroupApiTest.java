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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Phase 4: products with variants, each with its own stock. */
class ProductGroupApiTest extends ApiTest {

    Cookie sid;
    String group;
    String m;
    String l;

    @BeforeEach
    void setUp() throws Exception {
        sid = registerOwner();
        String body = mvc.perform(json(post("/api/product-groups"), """
                        {"name":"Camiseta","price":"25000","cost":"12000","variants":[
                          {"name":"M","stock":"3","barcode":"CAM-M"},
                          {"name":"L","stock":"2","price":"27000"}]}
                        """).cookie(sid))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.variants[0].name").value("Camiseta — M"))
                .andExpect(jsonPath("$.variants[0].variant").value("M"))
                .andExpect(jsonPath("$.variants[0].price").value("25000.00"))
                .andExpect(jsonPath("$.variants[0].cost").value("12000.00"))
                .andExpect(jsonPath("$.variants[1].price").value("27000.00"))
                .andReturn()
                .getResponse()
                .getContentAsString();
        group = JsonPath.read(body, "$.id");
        m = JsonPath.read(body, "$.variants[0].id");
        l = JsonPath.read(body, "$.variants[1].id");
    }

    @Test
    void eachVariantHasItsOwnStock() throws Exception {
        mvc.perform(json(post("/api/sales"), """
                        {"items":[{"productId":"%s","quantity":"1"}],"paymentMethod":"CASH"}
                        """.formatted(m)).cookie(sid))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.items[0].name").value("Camiseta — M"));
        mvc.perform(get("/api/inventory").cookie(sid))
                .andExpect(jsonPath("$.items[?(@.productId=='%s')].stock".formatted(m))
                        .value("2"))
                .andExpect(jsonPath("$.items[?(@.productId=='%s')].stock".formatted(l))
                        .value("2"));
        mvc.perform(get("/api/products").cookie(sid))
                .andExpect(jsonPath("$[?(@.id=='%s')].groupId".formatted(m)).value(group));
    }

    @Test
    void renamingAndDeletingTheGroupAffectsEveryVariant() throws Exception {
        mvc.perform(json(post("/api/product-groups/" + group + "/variants"), """
                        {"name":"XL","price":"29000","stock":"1"}
                        """)
                        .cookie(sid))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Camiseta — XL"));
        mvc.perform(json(put("/api/product-groups/" + group), """
                        {"name":"Polo"}
                        """).cookie(sid))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.variants.length()").value(3))
                .andExpect(jsonPath("$.variants[0].name").value("Polo — L"));
        mvc.perform(get("/api/products?q=polo").cookie(sid))
                .andExpect(jsonPath("$.length()").value(3));
        // A variant is edited through its label; its name follows the group.
        mvc.perform(json(put("/api/products/" + m), """
                        {"variant":"Mediana","price":"26000"}
                        """).cookie(sid))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Polo — Mediana"));
        mvc.perform(delete("/api/product-groups/" + group).cookie(sid)).andExpect(status().isNoContent());
        mvc.perform(get("/api/products").cookie(sid))
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void variantsNeedNamesAndCannotRepeat() throws Exception {
        mvc.perform(json(post("/api/product-groups"), """
                        {"name":"Jean","price":"50000","variants":[]}
                        """).cookie(sid))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("variants"));
        mvc.perform(json(post("/api/product-groups"), """
                        {"name":"Jean","price":"50000","variants":[{"name":"30"},{"name":"30"}]}
                        """).cookie(sid))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("variants[1].name"))
                .andExpect(jsonPath("$.errors[0].code").value("duplicate"));
        mvc.perform(json(post("/api/product-groups"), """
                        {"name":"Gorra","variants":[{"name":"Única"}]}
                        """).cookie(sid))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("variants[0].price"));
    }

    @Test
    void anotherBusinessCannotTouchGroups() throws Exception {
        Cookie other = registerOwner();
        mvc.perform(json(put("/api/product-groups/" + group), """
                        {"name":"X"}
                        """).cookie(other))
                .andExpect(status().isNotFound());
        mvc.perform(json(post("/api/product-groups/" + group + "/variants"), """
                        {"name":"S","price":"1"}
                        """)
                        .cookie(other))
                .andExpect(status().isNotFound());
        mvc.perform(delete("/api/product-groups/" + group).cookie(other)).andExpect(status().isNotFound());
        mvc.perform(get("/api/products").cookie(sid))
                .andExpect(jsonPath("$.length()").value(2));
    }
}
