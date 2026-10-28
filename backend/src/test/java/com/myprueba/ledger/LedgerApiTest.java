package com.myprueba.ledger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
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

class LedgerApiTest extends ApiTest {

    // Monday 2026-10-26 10:00 in Bogotá
    static final Instant NOW = Instant.parse("2026-10-26T15:00:00Z");

    Cookie sid;

    @BeforeEach
    void setUp() throws Exception {
        clock.set(NOW);
        sid = registerOwner();
    }

    @AfterEach
    void tearDown() {
        clock.reset();
    }

    String postJson(String path, String body, Cookie cookie) throws Exception {
        String response = mvc.perform(json(post(path), body).cookie(cookie))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return JsonPath.read(response, "$.id");
    }

    String product(String name, String price) throws Exception {
        return postJson("/api/products", """
                {"name":"%s","price":"%s"}
                """.formatted(name, price), sid);
    }

    @Test
    void emptyBusinessHasZeroBalanceAndNoMovements() throws Exception {
        mvc.perform(get("/api/balance").cookie(sid))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.period.from").value("2026-10-26"))
                .andExpect(jsonPath("$.sales").value("0.00"))
                .andExpect(jsonPath("$.expenses").value("0.00"))
                .andExpect(jsonPath("$.balance").value("0.00"));
        mvc.perform(get("/api/movements").cookie(sid))
                .andExpect(jsonPath("$.items.length()").value(0));
    }

    @Test
    void freeSaleAppearsTodayAndRaisesSales() throws Exception {
        String id = postJson("/api/sales", """
                {"amount":"15000","paymentMethod":"CASH","concept":"Almuerzo"}
                """, sid);
        mvc.perform(get("/api/sales/" + id).cookie(sid))
                .andExpect(jsonPath("$.kind").value("FREE"))
                .andExpect(jsonPath("$.total").value("15000.00"))
                .andExpect(jsonPath("$.occurredAt").value("2026-10-26T15:00:00Z"))
                .andExpect(jsonPath("$.items.length()").value(0));
        mvc.perform(get("/api/movements").cookie(sid))
                .andExpect(jsonPath("$.items[0].id").value(id))
                .andExpect(jsonPath("$.items[0].type").value("SALE"))
                .andExpect(jsonPath("$.items[0].amount").value("15000.00"))
                .andExpect(jsonPath("$.items[0].description").value("Almuerzo"));
        mvc.perform(get("/api/balance").cookie(sid))
                .andExpect(jsonPath("$.sales").value("15000.00"));
    }

    @Test
    void saleWithProductsComputesTotalFromLines() throws Exception {
        String gaseosa = product("Gaseosa", "3000");
        String pan = product("Pan", "500");
        String id = postJson("/api/sales", """
                {"items":[{"productId":"%s","quantity":"2"},{"productId":"%s","quantity":"4"}],"paymentMethod":"TRANSFER"}
                """.formatted(gaseosa, pan), sid);
        mvc.perform(get("/api/sales/" + id).cookie(sid))
                .andExpect(jsonPath("$.kind").value("ITEMS"))
                .andExpect(jsonPath("$.total").value("8000.00"))
                .andExpect(jsonPath("$.items[0].name").value("Gaseosa"))
                .andExpect(jsonPath("$.items[0].quantity").value("2"))
                .andExpect(jsonPath("$.items[0].unitPrice").value("3000.00"))
                .andExpect(jsonPath("$.items[0].lineTotal").value("6000.00"))
                .andExpect(jsonPath("$.items[1].name").value("Pan"));
        mvc.perform(get("/api/movements").cookie(sid))
                .andExpect(jsonPath("$.items[0].description").value("2 × Gaseosa, 4 × Pan"));
    }

    @Test
    void unitPriceCanBeAdjustedPerSaleAndWeightsAreSupported() throws Exception {
        String queso = product("Queso", "20000");
        String id = postJson("/api/sales", """
                {"items":[{"productId":"%s","quantity":"0.25","unitPrice":"18000"}],"paymentMethod":"CASH"}
                """.formatted(queso), sid);
        mvc.perform(get("/api/sales/" + id).cookie(sid))
                .andExpect(jsonPath("$.total").value("4500.00"))
                .andExpect(jsonPath("$.items[0].quantity").value("0.25"));
        mvc.perform(get("/api/products").cookie(sid))
                .andExpect(jsonPath("$[0].price").value("20000.00"));
    }

    @Test
    void saleKeepsNameAndPriceAfterProductChangesOrIsDeleted() throws Exception {
        String gaseosa = product("Gaseosa", "3000");
        String id = postJson("/api/sales", """
                {"items":[{"productId":"%s","quantity":"1"}],"paymentMethod":"CASH"}
                """.formatted(gaseosa), sid);
        mvc.perform(json(put("/api/products/" + gaseosa), """
                        {"name":"Gaseosa 400ml","price":"3500"}
                        """).cookie(sid)).andExpect(status().isOk());
        mvc.perform(get("/api/sales/" + id).cookie(sid))
                .andExpect(jsonPath("$.items[0].name").value("Gaseosa"))
                .andExpect(jsonPath("$.items[0].unitPrice").value("3000.00"));
        mvc.perform(delete("/api/products/" + gaseosa).cookie(sid)).andExpect(status().isNoContent());
        mvc.perform(get("/api/sales/" + id).cookie(sid))
                .andExpect(jsonPath("$.items[0].name").value("Gaseosa"))
                .andExpect(jsonPath("$.items[0].productId").doesNotExist())
                .andExpect(jsonPath("$.total").value("3000.00"));
    }

    @Test
    void saleWithDeletedProductKeepsItsLineWhenEdited() throws Exception {
        String gaseosa = product("Gaseosa", "3000");
        String id = postJson("/api/sales", """
                {"items":[{"productId":"%s","quantity":"2"}],"paymentMethod":"CASH"}
                """.formatted(gaseosa), sid);
        mvc.perform(delete("/api/products/" + gaseosa).cookie(sid)).andExpect(status().isNoContent());
        mvc.perform(json(put("/api/sales/" + id), """
                        {"items":[{"name":"Gaseosa","quantity":"3","unitPrice":"3000.00"}],"paymentMethod":"CARD"}
                        """).cookie(sid))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value("9000.00"))
                .andExpect(jsonPath("$.items[0].name").value("Gaseosa"))
                .andExpect(jsonPath("$.items[0].productId").doesNotExist());
    }

    @Test
    void lineWithoutProductNeedsNameAndPrice() throws Exception {
        mvc.perform(json(post("/api/sales"), """
                        {"items":[{"quantity":"1"}],"paymentMethod":"CASH"}
                        """).cookie(sid))
                .andExpect(status().isBadRequest())
                .andExpect(
                        jsonPath("$.errors[?(@.field=='items[0].name')].code").value("required"))
                .andExpect(jsonPath("$.errors[?(@.field=='items[0].unitPrice')].code")
                        .value("required"));
    }

    @Test
    void expenseLowersBalance() throws Exception {
        postJson("/api/sales", """
                {"amount":"100000","paymentMethod":"CASH"}
                """, sid);
        String id = postJson("/api/expenses", """
                {"amount":"50000","category":"RENT","paymentMethod":"TRANSFER"}
                """, sid);
        mvc.perform(get("/api/expenses/" + id).cookie(sid))
                .andExpect(jsonPath("$.category").value("RENT"))
                .andExpect(jsonPath("$.amount").value("50000.00"));
        mvc.perform(get("/api/balance").cookie(sid))
                .andExpect(jsonPath("$.expenses").value("50000.00"))
                .andExpect(jsonPath("$.balance").value("50000.00"));
    }

    @Test
    void monthlyBalanceCanBeNegative() throws Exception {
        postJson("/api/sales", """
                {"amount":"100000","paymentMethod":"CASH","occurredAt":"2026-10-02T15:00:00Z"}
                """, sid);
        postJson("/api/expenses", """
                {"amount":"130000","category":"PURCHASES","paymentMethod":"CASH","occurredAt":"2026-10-03T15:00:00Z"}
                """, sid);
        mvc.perform(get("/api/balance?period=month").cookie(sid))
                .andExpect(jsonPath("$.period.from").value("2026-10-01"))
                .andExpect(jsonPath("$.period.to").value("2026-10-31"))
                .andExpect(jsonPath("$.balance").value("-30000.00"));
    }

    @Test
    void totalsAreExactToTheCent() throws Exception {
        for (int i = 0; i < 3; i++) {
            postJson("/api/sales", """
                    {"amount":"0.10","paymentMethod":"CASH"}
                    """, sid);
        }
        mvc.perform(get("/api/balance").cookie(sid))
                .andExpect(jsonPath("$.sales").value("0.30"));
    }

    @Test
    void invalidAmountsAreRejectedNamingTheField() throws Exception {
        for (String amount : new String[] {"0", "-5", "1.234"}) {
            mvc.perform(json(post("/api/sales"), """
                            {"amount":"%s","paymentMethod":"CASH"}
                            """.formatted(amount)).cookie(sid))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors[0].field").value("amount"));
            mvc.perform(json(post("/api/expenses"), """
                            {"amount":"%s","category":"RENT","paymentMethod":"CASH"}
                            """.formatted(amount)).cookie(sid))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors[0].field").value("amount"));
        }
    }

    @Test
    void missingOrUnknownFieldsAreReported() throws Exception {
        mvc.perform(json(post("/api/sales"), """
                        {"paymentMethod":"BITCOIN"}
                        """).cookie(sid))
                .andExpect(status().isBadRequest())
                .andExpect(
                        jsonPath("$.errors[?(@.field=='paymentMethod')].code").value("invalid_value"))
                .andExpect(jsonPath("$.errors[?(@.field=='amount')].code").value("sale_kind"));
        mvc.perform(json(post("/api/expenses"), """
                        {"amount":"10"}
                        """).cookie(sid))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[?(@.field=='category')].code").value("required"))
                .andExpect(
                        jsonPath("$.errors[?(@.field=='paymentMethod')].code").value("required"));
    }

    @Test
    void saleCannotHaveBothAmountAndItems() throws Exception {
        String pan = product("Pan", "500");
        mvc.perform(json(post("/api/sales"), """
                        {"amount":"10","items":[{"productId":"%s","quantity":"1"}],"paymentMethod":"CASH"}
                        """.formatted(pan)).cookie(sid))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].code").value("sale_kind"));
    }

    @Test
    void futureDatesAreRejectedButEarlierTodayIsAccepted() throws Exception {
        mvc.perform(json(post("/api/sales"), """
                        {"amount":"10","paymentMethod":"CASH","occurredAt":"2026-10-27T15:00:00Z"}
                        """).cookie(sid))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("occurredAt"))
                .andExpect(jsonPath("$.errors[0].code").value("future_date"));
        // later today, local time: still today, accepted
        postJson("/api/sales", """
                {"amount":"10","paymentMethod":"CASH","occurredAt":"2026-10-26T23:00:00Z"}
                """, sid);
        mvc.perform(json(post("/api/expenses"), """
                        {"amount":"10","category":"RENT","paymentMethod":"CASH","occurredAt":"mañana"}
                        """).cookie(sid))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].code").value("invalid_format"));
    }

    @Test
    void sundayLateNightIsNotInThisWeek() throws Exception {
        postJson("/api/sales", """
                {"amount":"777","paymentMethod":"CASH","occurredAt":"2026-10-26T04:30:00Z"}
                """, sid);
        mvc.perform(get("/api/balance?period=week").cookie(sid))
                .andExpect(jsonPath("$.sales").value("0.00"));
        mvc.perform(get("/api/balance?period=range&from=2026-10-25&to=2026-10-25")
                        .cookie(sid))
                .andExpect(jsonPath("$.sales").value("777.00"));
    }

    @Test
    void editingAndDeletingUpdatesBalance() throws Exception {
        String sale = postJson("/api/sales", """
                {"amount":"1000","paymentMethod":"CASH"}
                """, sid);
        String expense = postJson("/api/expenses", """
                {"amount":"300","category":"OTHER","paymentMethod":"CASH"}
                """, sid);
        mvc.perform(json(put("/api/sales/" + sale), """
                        {"amount":"2500","paymentMethod":"CARD","note":"corregida"}
                        """).cookie(sid))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value("2500.00"))
                .andExpect(jsonPath("$.note").value("corregida"));
        mvc.perform(json(put("/api/expenses/" + expense), """
                        {"amount":"500","category":"TRANSPORT","paymentMethod":"CASH"}
                        """).cookie(sid)).andExpect(status().isOk());
        mvc.perform(get("/api/balance").cookie(sid))
                .andExpect(jsonPath("$.balance").value("2000.00"));
        mvc.perform(delete("/api/sales/" + sale).cookie(sid)).andExpect(status().isNoContent());
        mvc.perform(delete("/api/expenses/" + expense).cookie(sid)).andExpect(status().isNoContent());
        mvc.perform(get("/api/balance").cookie(sid))
                .andExpect(jsonPath("$.balance").value("0.00"));
        mvc.perform(get("/api/sales/" + sale).cookie(sid)).andExpect(status().isNotFound());
    }

    @Test
    void editingASaleCanSwitchToProducts() throws Exception {
        String pan = product("Pan", "500");
        String sale = postJson("/api/sales", """
                {"amount":"1000","paymentMethod":"CASH"}
                """, sid);
        mvc.perform(json(put("/api/sales/" + sale), """
                        {"items":[{"productId":"%s","quantity":"3"}],"paymentMethod":"CASH"}
                        """.formatted(pan)).cookie(sid))
                .andExpect(jsonPath("$.kind").value("ITEMS"))
                .andExpect(jsonPath("$.total").value("1500.00"));
    }

    @Test
    void movementsAreNewestFirstAndFilterable() throws Exception {
        postJson("/api/sales", """
                {"amount":"1","paymentMethod":"CASH","occurredAt":"2026-10-26T13:00:00Z"}
                """, sid);
        postJson("/api/expenses", """
                {"amount":"2","category":"RENT","paymentMethod":"CASH","occurredAt":"2026-10-26T14:00:00Z","concept":"Local"}
                """, sid);
        mvc.perform(get("/api/movements").cookie(sid))
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.items[0].type").value("EXPENSE"))
                .andExpect(jsonPath("$.items[0].category").value("RENT"))
                .andExpect(jsonPath("$.items[1].type").value("SALE"));
        mvc.perform(get("/api/movements?type=expenses").cookie(sid))
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].type").value("EXPENSE"));
        mvc.perform(get("/api/movements?type=sales").cookie(sid))
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].type").value("SALE"));
        mvc.perform(get("/api/movements?type=gifts").cookie(sid)).andExpect(status().isBadRequest());
    }

    @Test
    void unknownProductInSaleIsNotFound() throws Exception {
        String productOfOther = mvc.perform(json(post("/api/products"), """
                        {"name":"Ajeno","price":"1"}
                        """).cookie(registerOwner()))
                .andReturn()
                .getResponse()
                .getContentAsString();
        String id = JsonPath.read(productOfOther, "$.id");
        mvc.perform(json(post("/api/sales"), """
                        {"items":[{"productId":"%s","quantity":"1"}],"paymentMethod":"CASH"}
                        """.formatted(id)).cookie(sid))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("product_not_found"));
    }

    @Test
    void anotherBusinessCannotSeeEditOrDeleteMovements() throws Exception {
        Cookie other = registerOwner();
        String sale = postJson("/api/sales", """
                {"amount":"1000","paymentMethod":"CASH"}
                """, sid);
        String expense = postJson("/api/expenses", """
                {"amount":"300","category":"OTHER","paymentMethod":"CASH"}
                """, sid);
        for (String path : new String[] {"/api/sales/" + sale, "/api/expenses/" + expense}) {
            mvc.perform(get(path).cookie(other)).andExpect(status().isNotFound());
            mvc.perform(delete(path).cookie(other)).andExpect(status().isNotFound());
        }
        mvc.perform(json(put("/api/sales/" + sale), """
                        {"amount":"1","paymentMethod":"CASH"}
                        """).cookie(other)).andExpect(status().isNotFound());
        mvc.perform(json(put("/api/expenses/" + expense), """
                        {"amount":"1","category":"OTHER","paymentMethod":"CASH"}
                        """).cookie(other)).andExpect(status().isNotFound());
        mvc.perform(get("/api/movements").cookie(other))
                .andExpect(jsonPath("$.items.length()").value(0));
        mvc.perform(get("/api/balance").cookie(other))
                .andExpect(jsonPath("$.sales").value("0.00"));
        mvc.perform(get("/api/balance").cookie(sid))
                .andExpect(jsonPath("$.balance").value("700.00"));
    }

    @Test
    void balanceStaysFastWithThousandsOfMovements() throws Exception {
        for (int i = 0; i < 1000; i++) {
            mvc.perform(json(post("/api/sales"), """
                    {"amount":"1000","paymentMethod":"CASH"}
                    """).cookie(sid));
            mvc.perform(json(post("/api/expenses"), """
                    {"amount":"10","category":"OTHER","paymentMethod":"CASH"}
                    """).cookie(sid));
        }
        long[] durations = new long[200];
        for (int i = 0; i < durations.length; i++) {
            long start = System.nanoTime();
            mvc.perform(get("/api/balance?period=month").cookie(sid)).andExpect(status().isOk());
            durations[i] = System.nanoTime() - start;
        }
        java.util.Arrays.sort(durations);
        long p95Millis = durations[(int) (durations.length * 0.95)] / 1_000_000;
        assertThat(p95Millis).isLessThanOrEqualTo(300);
        mvc.perform(get("/api/balance?period=month").cookie(sid))
                .andExpect(jsonPath("$.balance").value("990000.00"));
    }
}
