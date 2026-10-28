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
import net.minidev.json.JSONArray;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.ResultActions;

/** Phase 3: purchases from suppliers, stock in, and accounts payable. */
class PayablesApiTest extends ApiTest {

    static final Instant NOW = Instant.parse("2026-10-28T15:00:00Z");

    Cookie sid;
    String andina;
    String gaseosa;

    @BeforeEach
    void setUp() throws Exception {
        clock.set(NOW);
        sid = registerOwner();
        andina = created(mvc.perform(json(post("/api/suppliers"), """
                {"name":"Distribuidora Andina"}
                """).cookie(sid)));
        gaseosa = created(mvc.perform(json(post("/api/products"), """
                {"name":"Gaseosa","price":"3000","cost":"2000","stock":"5"}
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

    String stockOf(String productId) throws Exception {
        String body = mvc.perform(get("/api/inventory").cookie(sid))
                .andReturn()
                .getResponse()
                .getContentAsString();
        JSONArray stock = JsonPath.read(body, "$.items[?(@.productId=='%s')].stock".formatted(productId));
        return (String) stock.get(0);
    }

    String purchase(String quantity, String extra) throws Exception {
        return created(mvc.perform(json(post("/api/expenses"), """
                {"category":"PURCHASES","supplierId":"%s","items":[{"productId":"%s","quantity":"%s"}]%s}
                """.formatted(andina, gaseosa, quantity, extra))
                .cookie(sid)));
    }

    @Test
    void paidPurchaseRaisesStockAndExpenses() throws Exception {
        String id = purchase("24", ",\"paymentMethod\":\"CASH\"");
        mvc.perform(get("/api/expenses/" + id).cookie(sid))
                .andExpect(jsonPath("$.amount").value("48000.00"))
                .andExpect(jsonPath("$.supplierName").value("Distribuidora Andina"))
                .andExpect(jsonPath("$.status").value("PAID"))
                .andExpect(jsonPath("$.items[0].name").value("Gaseosa"))
                .andExpect(jsonPath("$.items[0].unitCost").value("2000.00"))
                .andExpect(jsonPath("$.items[0].quantity").value("24"));
        assertThat(stockOf(gaseosa)).isEqualTo("29");
        mvc.perform(get("/api/inventory/" + gaseosa + "/movements").cookie(sid))
                .andExpect(jsonPath("$[0].type").value("PURCHASE"))
                .andExpect(jsonPath("$[0].change").value("24"));
        mvc.perform(get("/api/balance").cookie(sid))
                .andExpect(jsonPath("$.expenses").value("48000.00"));
    }

    @Test
    void editingAndDeletingAPurchaseMoveStockBack() throws Exception {
        String id = purchase("24", ",\"paymentMethod\":\"CASH\"");
        mvc.perform(json(put("/api/expenses/" + id), """
                        {"category":"PURCHASES","supplierId":"%s","items":[{"productId":"%s","quantity":"12","unitCost":"1800"}],"paymentMethod":"CASH"}
                        """.formatted(andina, gaseosa))
                        .cookie(sid))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.amount").value("21600.00"));
        assertThat(stockOf(gaseosa)).isEqualTo("17");
        mvc.perform(delete("/api/expenses/" + id).cookie(sid)).andExpect(status().isNoContent());
        assertThat(stockOf(gaseosa)).isEqualTo("5");
    }

    @Test
    void creditPurchaseCreatesPayableWithoutCashOut() throws Exception {
        mvc.perform(json(post("/api/expenses"), """
                        {"amount":"100000","category":"PURCHASES","status":"CREDIT"}
                        """).cookie(sid))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("supplierId"));
        created(mvc.perform(json(post("/api/expenses"), """
                {"amount":"100000","category":"PURCHASES","status":"CREDIT","supplierId":"%s","dueDate":"2026-11-15"}
                """.formatted(andina)).cookie(sid)));
        mvc.perform(get("/api/payables").cookie(sid))
                .andExpect(jsonPath("$.total").value("100000.00"))
                .andExpect(jsonPath("$.suppliers[0].name").value("Distribuidora Andina"))
                .andExpect(jsonPath("$.suppliers[0].debt").value("100000.00"))
                .andExpect(jsonPath("$.suppliers[0].oldestDebtAt").value("2026-10-28T15:00:00Z"))
                .andExpect(jsonPath("$.suppliers[0].overdue").value(false));
        mvc.perform(get("/api/balance").cookie(sid))
                .andExpect(jsonPath("$.expenses").value("0.00"))
                .andExpect(jsonPath("$.payables").value("100000.00"));
    }

    @Test
    void supplierPaymentsReduceDebtAndCountAsExpenses() throws Exception {
        created(mvc.perform(json(post("/api/expenses"), """
                {"amount":"100000","category":"PURCHASES","status":"CREDIT","supplierId":"%s"}
                """.formatted(andina)).cookie(sid)));
        String payment = created(mvc.perform(
                json(post("/api/supplier-payments"), """
                {"supplierId":"%s","amount":"40000","paymentMethod":"TRANSFER"}
                """.formatted(andina)).cookie(sid)));
        mvc.perform(get("/api/supplier-payments/" + payment).cookie(sid))
                .andExpect(jsonPath("$.supplierName").value("Distribuidora Andina"));
        mvc.perform(get("/api/payables").cookie(sid))
                .andExpect(jsonPath("$.suppliers[0].debt").value("60000.00"));
        mvc.perform(get("/api/balance").cookie(sid))
                .andExpect(jsonPath("$.expenses").value("40000.00"))
                .andExpect(jsonPath("$.balance").value("-40000.00"));
        mvc.perform(get("/api/movements?type=expenses").cookie(sid))
                .andExpect(jsonPath("$.items[?(@.type=='SUPPLIER_PAYMENT')].supplierName")
                        .value("Distribuidora Andina"));
        mvc.perform(json(post("/api/supplier-payments"), """
                        {"supplierId":"%s","amount":"70000","paymentMethod":"CASH"}
                        """.formatted(andina)).cookie(sid))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].code").value("exceeds_debt"));
        mvc.perform(get("/api/suppliers/" + andina + "/account").cookie(sid))
                .andExpect(jsonPath("$.debt").value("60000.00"))
                .andExpect(jsonPath("$.entries.length()").value(2));
        mvc.perform(delete("/api/suppliers/" + andina).cookie(sid))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("supplier_has_debt"));
    }

    @Test
    void purchaseHistoryIsPerSupplier() throws Exception {
        String alpina = created(mvc.perform(json(post("/api/suppliers"), """
                {"name":"Alpina"}
                """).cookie(sid)));
        purchase("10", ",\"paymentMethod\":\"CASH\"");
        created(mvc.perform(json(post("/api/expenses"), """
                {"amount":"5000","category":"PURCHASES","paymentMethod":"CASH","supplierId":"%s","concept":"Yogures"}
                """.formatted(alpina)).cookie(sid)));
        mvc.perform(get("/api/suppliers/" + andina + "/purchases").cookie(sid))
                .andExpect(jsonPath("$.count").value(1))
                .andExpect(jsonPath("$.total").value("20000.00"))
                .andExpect(jsonPath("$.items[0].description").value("10 × Gaseosa"));
        mvc.perform(get("/api/suppliers/" + alpina + "/purchases").cookie(sid))
                .andExpect(jsonPath("$.items[0].description").value("Yogures"));
    }

    @Test
    void purchasesCannotMixAmountAndItemsAndUseTheirOwnCategory() throws Exception {
        mvc.perform(json(post("/api/expenses"), """
                        {"amount":"10","category":"RENT","paymentMethod":"CASH","items":[{"productId":"%s","quantity":"1"}]}
                        """.formatted(gaseosa)).cookie(sid))
                .andExpect(status().isBadRequest());
        mvc.perform(json(post("/api/expenses"), """
                        {"category":"RENT","paymentMethod":"CASH","items":[{"productId":"%s","quantity":"1"}]}
                        """.formatted(gaseosa)).cookie(sid))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("category"));
    }

    @Test
    void anotherBusinessCannotUseSuppliersOrPayables() throws Exception {
        Cookie other = registerOwner();
        mvc.perform(json(post("/api/expenses"), """
                        {"amount":"1","category":"OTHER","paymentMethod":"CASH","supplierId":"%s"}
                        """.formatted(andina)).cookie(other))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("supplier_not_found"));
        mvc.perform(json(post("/api/supplier-payments"), """
                        {"supplierId":"%s","amount":"1","paymentMethod":"CASH"}
                        """.formatted(andina)).cookie(other))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/suppliers/" + andina + "/account").cookie(other)).andExpect(status().isNotFound());
        mvc.perform(get("/api/suppliers/" + andina + "/purchases").cookie(other))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/payables").cookie(other))
                .andExpect(jsonPath("$.total").value("0.00"));
    }
}
