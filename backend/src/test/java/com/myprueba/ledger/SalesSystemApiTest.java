package com.myprueba.ledger;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.myprueba.support.ApiTest;
import jakarta.servlet.http.Cookie;
import java.time.Instant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.ResultActions;

/** Sales system: discounts, cash received and change, sale numbers and digital receipts. */
class SalesSystemApiTest extends ApiTest {

    static final Instant NOW = Instant.parse("2026-10-28T15:00:00Z");

    Cookie sid;
    String gaseosa;
    String pan;

    @BeforeEach
    void setUp() throws Exception {
        clock.set(NOW);
        sid = registerOwner();
        gaseosa = created(mvc.perform(json(post("/api/products"), """
                {"name":"Gaseosa","price":"3000"}
                """).cookie(sid)));
        pan = created(mvc.perform(json(post("/api/products"), """
                {"name":"Pan","price":"500"}
                """).cookie(sid)));
    }

    @AfterEach
    void tearDown() {
        clock.reset();
    }

    String created(ResultActions result) throws Exception {
        return JsonPath.read(
                result.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.id");
    }

    String items() {
        return """
                [{"productId":"%s","quantity":"2","discountPercent":"10"},{"productId":"%s","quantity":"4"}]
                """.formatted(gaseosa, pan).strip();
    }

    @Test
    void discountsPerLineAndPerSale() throws Exception {
        String id = created(
                mvc.perform(json(post("/api/sales"), """
                {"items":%s,"discount":"500","paymentMethod":"CARD"}
                """.formatted(items())).cookie(sid)));
        mvc.perform(get("/api/sales/" + id).cookie(sid))
                .andExpect(jsonPath("$.subtotal").value("8000.00"))
                .andExpect(jsonPath("$.items[0].lineTotal").value("6000.00"))
                .andExpect(jsonPath("$.items[0].discount").value("600.00"))
                .andExpect(jsonPath("$.items[1].discount").value("0.00"))
                .andExpect(jsonPath("$.discountTotal").value("1100.00"))
                .andExpect(jsonPath("$.total").value("6900.00"));
        mvc.perform(get("/api/balance").cookie(sid))
                .andExpect(jsonPath("$.sales").value("6900.00"));
        // A percentage of the whole sale works too, after line discounts.
        mvc.perform(json(put("/api/sales/" + id), """
                        {"items":%s,"discountPercent":"50","paymentMethod":"CARD"}
                        """.formatted(items())).cookie(sid))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.discountTotal").value("4300.00"))
                .andExpect(jsonPath("$.total").value("3700.00"));
    }

    @Test
    void discountsCannotExceedWhatTheyApplyTo() throws Exception {
        mvc.perform(json(post("/api/sales"), """
                        {"items":[{"productId":"%s","quantity":"2","discount":"9000"}],"paymentMethod":"CASH"}
                        """.formatted(gaseosa)).cookie(sid))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("items[0].discount"))
                .andExpect(jsonPath("$.errors[0].code").value("exceeds_line"));
        mvc.perform(json(post("/api/sales"), """
                        {"items":[{"productId":"%s","quantity":"1"}],"discount":"3001","paymentMethod":"CASH"}
                        """.formatted(gaseosa)).cookie(sid))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("discount"))
                .andExpect(jsonPath("$.errors[0].code").value("exceeds_subtotal"));
        mvc.perform(json(post("/api/sales"), """
                        {"items":[{"productId":"%s","quantity":"1","discountPercent":"101"}],"paymentMethod":"CASH"}
                        """.formatted(gaseosa)).cookie(sid))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("items[0].discountPercent"));
        mvc.perform(json(post("/api/sales"), """
                        {"amount":"1000","discount":"100","paymentMethod":"CASH"}
                        """).cookie(sid))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("discount"))
                .andExpect(jsonPath("$.errors[0].code").value("items_only"));
        mvc.perform(json(post("/api/sales"), """
                        {"items":[{"productId":"%s","quantity":"1","discount":"100","discountPercent":"5"}],"paymentMethod":"CASH"}
                        """.formatted(gaseosa)).cookie(sid))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("items[0].discount"));
    }

    @Test
    void cashReceivedGivesTheChange() throws Exception {
        String id = created(
                mvc.perform(json(post("/api/sales"), """
                {"items":%s,"discount":"500","paymentMethod":"CASH","received":"10.000"}
                """.formatted(items()).replace("10.000", "10000"))
                        .cookie(sid)));
        mvc.perform(get("/api/sales/" + id).cookie(sid))
                .andExpect(jsonPath("$.received").value("10000.00"))
                .andExpect(jsonPath("$.change").value("3100.00"));
        mvc.perform(json(post("/api/sales"), """
                        {"items":%s,"discount":"500","paymentMethod":"CASH","received":"5000"}
                        """.formatted(items())).cookie(sid))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("received"))
                .andExpect(jsonPath("$.errors[0].code").value("less_than_total"));
        mvc.perform(json(post("/api/sales"), """
                        {"amount":"1000","paymentMethod":"CARD","received":"2000"}
                        """).cookie(sid))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].code").value("cash_only"));
    }

    @Test
    void salesAreNumberedPerBusiness() throws Exception {
        String first = created(mvc.perform(json(post("/api/sales"), """
                {"amount":"1000","paymentMethod":"CASH"}
                """).cookie(sid)));
        Cookie other = registerOwner();
        mvc.perform(json(post("/api/sales"), """
                        {"amount":"1000","paymentMethod":"CASH"}
                        """).cookie(other))
                .andExpect(jsonPath("$.number").value(1));
        String second = created(mvc.perform(json(post("/api/sales"), """
                {"amount":"2000","paymentMethod":"CASH"}
                """).cookie(sid)));
        mvc.perform(get("/api/sales/" + first).cookie(sid))
                .andExpect(jsonPath("$.number").value(1));
        mvc.perform(get("/api/sales/" + second).cookie(sid))
                .andExpect(jsonPath("$.number").value(2));
        // Editing keeps the number.
        mvc.perform(json(put("/api/sales/" + first), """
                        {"amount":"1500","paymentMethod":"CASH"}
                        """).cookie(sid))
                .andExpect(jsonPath("$.number").value(1));
    }

    @Test
    void receiptCarriesBusinessAndCustomerData() throws Exception {
        mvc.perform(json(put("/api/business"), """
                        {"name":"Tienda Doña Rosa","category":"LOCAL_MARKET","document":"1061700000-1",
                         "phone":"312 000 0000","address":"Cra 7 # 12-34, Popayán"}
                        """).cookie(sid))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.document").value("1061700000-1"));
        String juan = created(mvc.perform(json(post("/api/customers"), """
                {"name":"Juan","phone":"300 123 4567","document":"10203040"}
                """).cookie(sid)));
        String id = created(mvc.perform(
                json(post("/api/sales"), """
                {"items":%s,"status":"CREDIT","customerId":"%s","dueDate":"2026-11-15"}
                """.formatted(items(), juan)).cookie(sid)));
        mvc.perform(get("/api/sales/" + id + "/receipt").cookie(sid))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.business.name").value("Tienda Doña Rosa"))
                .andExpect(jsonPath("$.business.document").value("1061700000-1"))
                .andExpect(jsonPath("$.business.address").value("Cra 7 # 12-34, Popayán"))
                .andExpect(jsonPath("$.number").value(1))
                .andExpect(jsonPath("$.customer.name").value("Juan"))
                .andExpect(jsonPath("$.customer.document").value("10203040"))
                .andExpect(jsonPath("$.customer.phone").value("300 123 4567"))
                .andExpect(jsonPath("$.items[0].name").value("Gaseosa"))
                .andExpect(jsonPath("$.items[0].discount").value("600.00"))
                .andExpect(jsonPath("$.subtotal").value("8000.00"))
                .andExpect(jsonPath("$.total").value("7400.00"))
                .andExpect(jsonPath("$.status").value("CREDIT"))
                .andExpect(jsonPath("$.dueDate").value("2026-11-15"));
        Cookie other = registerOwner();
        mvc.perform(get("/api/sales/" + id + "/receipt").cookie(other)).andExpect(status().isNotFound());
    }
}
