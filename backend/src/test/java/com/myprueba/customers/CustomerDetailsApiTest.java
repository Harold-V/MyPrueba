package com.myprueba.customers;

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

/** Phase 2: full customer record and purchase history. */
class CustomerDetailsApiTest extends ApiTest {

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
    void customerKeepsDocumentAndAddress() throws Exception {
        String id = created(mvc.perform(json(post("/api/customers"), """
                {"name":"Juan","document":" 1.020.304.050 ","address":"Cra 7 # 12-34, Popayán"}
                """).cookie(sid)));
        mvc.perform(get("/api/customers").cookie(sid))
                .andExpect(jsonPath("$[0].document").value("1.020.304.050"))
                .andExpect(jsonPath("$[0].address").value("Cra 7 # 12-34, Popayán"));
        mvc.perform(json(put("/api/customers/" + id), """
                        {"name":"Juan","document":"98765","address":null}
                        """).cookie(sid))
                .andExpect(jsonPath("$.document").value("98765"))
                .andExpect(jsonPath("$.address").doesNotExist());
        mvc.perform(json(post("/api/customers"), """
                        {"name":"X","document":"%s","address":"%s"}
                        """.formatted("1".repeat(31), "a".repeat(201)))
                        .cookie(sid))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[?(@.field=='document')].code").value("too_long"))
                .andExpect(jsonPath("$.errors[?(@.field=='address')].code").value("too_long"));
    }

    @Test
    void purchaseHistoryListsEverySaleOfTheCustomer() throws Exception {
        String juan = created(mvc.perform(json(post("/api/customers"), """
                {"name":"Juan"}
                """).cookie(sid)));
        String ana = created(mvc.perform(json(post("/api/customers"), """
                {"name":"Ana"}
                """).cookie(sid)));
        String pan = created(mvc.perform(json(post("/api/products"), """
                {"name":"Pan","price":"500"}
                """).cookie(sid)));
        created(mvc.perform(json(post("/api/sales"), """
                {"items":[{"productId":"%s","quantity":"20"}],"paymentMethod":"CASH","customerId":"%s","occurredAt":"2026-10-01T15:00:00Z"}
                """.formatted(pan, juan)).cookie(sid)));
        created(mvc.perform(json(post("/api/sales"), """
                {"amount":"5000","status":"CREDIT","customerId":"%s","concept":"Mercado"}
                """.formatted(juan)).cookie(sid)));
        created(mvc.perform(json(post("/api/sales"), """
                {"amount":"999","paymentMethod":"CASH","customerId":"%s"}
                """.formatted(ana)).cookie(sid)));
        mvc.perform(get("/api/sales").cookie(sid)).andExpect(status().isMethodNotAllowed());

        mvc.perform(get("/api/customers/" + juan + "/purchases").cookie(sid))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value("15000.00"))
                .andExpect(jsonPath("$.count").value(2))
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.items[0].status").value("CREDIT"))
                .andExpect(jsonPath("$.items[0].description").value("Mercado"))
                .andExpect(jsonPath("$.items[1].status").value("PAID"))
                .andExpect(jsonPath("$.items[1].description").value("20 × Pan"))
                .andExpect(jsonPath("$.items[1].total").value("10000.00"));
    }

    @Test
    void anotherBusinessCannotSeeThePurchaseHistory() throws Exception {
        String juan = created(mvc.perform(json(post("/api/customers"), """
                {"name":"Juan"}
                """).cookie(sid)));
        mvc.perform(get("/api/customers/" + juan + "/purchases").cookie(registerOwner()))
                .andExpect(status().isNotFound());
    }
}
