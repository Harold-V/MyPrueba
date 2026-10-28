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
import org.springframework.test.web.servlet.ResultActions;

/** Phase 4: categories, barcodes and search. */
class CatalogApiTest extends ApiTest {

    Cookie sid;

    @BeforeEach
    void setUp() throws Exception {
        sid = registerOwner();
    }

    String created(ResultActions result) throws Exception {
        return JsonPath.read(
                result.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.id");
    }

    @Test
    void categoriesOrganiseProducts() throws Exception {
        String bebidas =
                created(mvc.perform(json(post("/api/product-categories"), """
                {"name":"Bebidas"}
                """).cookie(sid)));
        created(mvc.perform(json(post("/api/product-categories"), """
                {"name":"Aseo"}
                """).cookie(sid)));
        mvc.perform(json(post("/api/product-categories"), """
                        {"name":"bebidas"}
                        """).cookie(sid))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("category_name_taken"));
        mvc.perform(get("/api/product-categories").cookie(sid))
                .andExpect(jsonPath("$[0].name").value("Aseo"))
                .andExpect(jsonPath("$[1].name").value("Bebidas"));

        created(mvc.perform(json(post("/api/products"), """
                {"name":"Gaseosa","price":"3000","categoryId":"%s"}
                """.formatted(bebidas)).cookie(sid)));
        created(mvc.perform(json(post("/api/products"), """
                {"name":"Jabón","price":"2500"}
                """).cookie(sid)));
        mvc.perform(get("/api/products?categoryId=" + bebidas).cookie(sid))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].name").value("Gaseosa"))
                .andExpect(jsonPath("$[0].categoryName").value("Bebidas"));
        mvc.perform(get("/api/inventory?categoryId=" + bebidas).cookie(sid))
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.summary.references").value(2));

        mvc.perform(json(put("/api/product-categories/" + bebidas), """
                        {"name":"Bebidas frías"}
                        """).cookie(sid))
                .andExpect(status().isOk());
        mvc.perform(delete("/api/product-categories/" + bebidas).cookie(sid)).andExpect(status().isNoContent());
        mvc.perform(get("/api/products?q=gaseosa").cookie(sid))
                .andExpect(jsonPath("$[0].categoryId").doesNotExist());
    }

    @Test
    void barcodesAreUniqueAndSearchable() throws Exception {
        String gaseosa = created(mvc.perform(json(post("/api/products"), """
                {"name":"Gaseosa","price":"3000","barcode":"7702004003508"}
                """).cookie(sid)));
        mvc.perform(json(post("/api/products"), """
                        {"name":"Otra","price":"1000","barcode":" 7702004003508 "}
                        """).cookie(sid))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("barcode_taken"));
        mvc.perform(get("/api/products?q=7702004").cookie(sid))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].barcode").value("7702004003508"));
        mvc.perform(get("/api/products?q=GASE").cookie(sid))
                .andExpect(jsonPath("$.length()").value(1));
        mvc.perform(get("/api/products?q=pan").cookie(sid))
                .andExpect(jsonPath("$.length()").value(0));
        // Editing keeps its own code.
        mvc.perform(json(put("/api/products/" + gaseosa), """
                        {"name":"Gaseosa 400 ml","price":"3200","barcode":"7702004003508"}
                        """).cookie(sid))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.barcode").value("7702004003508"));
        mvc.perform(get("/api/inventory?q=400").cookie(sid))
                .andExpect(jsonPath("$.items.length()").value(1));
    }

    @Test
    void anotherBusinessCannotUseCategories() throws Exception {
        String bebidas =
                created(mvc.perform(json(post("/api/product-categories"), """
                {"name":"Bebidas"}
                """).cookie(sid)));
        Cookie other = registerOwner();
        mvc.perform(get("/api/product-categories").cookie(other))
                .andExpect(jsonPath("$.length()").value(0));
        mvc.perform(json(put("/api/product-categories/" + bebidas), """
                        {"name":"X"}
                        """).cookie(other))
                .andExpect(status().isNotFound());
        mvc.perform(delete("/api/product-categories/" + bebidas).cookie(other)).andExpect(status().isNotFound());
        mvc.perform(json(post("/api/products"), """
                        {"name":"Gaseosa","price":"3000","categoryId":"%s"}
                        """.formatted(bebidas)).cookie(other))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("category_not_found"));
    }
}
