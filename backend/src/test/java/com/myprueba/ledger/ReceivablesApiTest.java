package com.myprueba.ledger;

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
import org.springframework.test.web.servlet.ResultActions;

class ReceivablesApiTest extends ApiTest {

    static final Instant NOW = Instant.parse("2026-10-26T15:00:00Z");

    Cookie sid;
    String juan;

    @BeforeEach
    void setUp() throws Exception {
        clock.set(NOW);
        sid = registerOwner();
        juan = customer(sid, "Juan");
    }

    @AfterEach
    void tearDown() {
        clock.reset();
    }

    String created(ResultActions result) throws Exception {
        return JsonPath.read(
                result.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.id");
    }

    String customer(Cookie cookie, String name) throws Exception {
        return created(
                mvc.perform(json(post("/api/customers"), """
                {"name":"%s"}
                """.formatted(name)).cookie(cookie)));
    }

    String creditSale(String customerId, String amount) throws Exception {
        return created(mvc.perform(
                json(post("/api/sales"), """
                {"amount":"%s","paymentMethod":"CREDIT","customerId":"%s"}
                """.formatted(amount, customerId)).cookie(sid)));
    }

    ResultActions pay(String customerId, String amount) throws Exception {
        return mvc.perform(json(post("/api/customer-payments"), """
                {"customerId":"%s","amount":"%s","paymentMethod":"CASH"}
                """.formatted(customerId, amount))
                .cookie(sid));
    }

    ResultActions receivables() throws Exception {
        return mvc.perform(get("/api/receivables").cookie(sid)).andExpect(status().isOk());
    }

    @Test
    void creditSaleCreatesDebtWithoutCountingAsIncome() throws Exception {
        String sale = creditSale(juan, "20000");
        mvc.perform(get("/api/sales/" + sale).cookie(sid))
                .andExpect(jsonPath("$.paymentMethod").value("CREDIT"))
                .andExpect(jsonPath("$.customerId").value(juan))
                .andExpect(jsonPath("$.customerName").value("Juan"));
        receivables()
                .andExpect(jsonPath("$.total").value("20000.00"))
                .andExpect(jsonPath("$.customers[0].name").value("Juan"))
                .andExpect(jsonPath("$.customers[0].debt").value("20000.00"));
        mvc.perform(get("/api/balance").cookie(sid))
                .andExpect(jsonPath("$.sales").value("0.00"))
                .andExpect(jsonPath("$.payments").value("0.00"))
                .andExpect(jsonPath("$.receivable").value("20000.00"))
                .andExpect(jsonPath("$.balance").value("0.00"));
        mvc.perform(get("/api/movements").cookie(sid))
                .andExpect(jsonPath("$.items[0].paymentMethod").value("CREDIT"))
                .andExpect(jsonPath("$.items[0].customerName").value("Juan"));
    }

    @Test
    void creditRequiresACustomer() throws Exception {
        mvc.perform(json(post("/api/sales"), """
                        {"amount":"100","paymentMethod":"CREDIT"}
                        """).cookie(sid))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("customerId"))
                .andExpect(jsonPath("$.errors[0].code").value("required"));
    }

    @Test
    void cashSaleCanCarryACustomer() throws Exception {
        String sale = created(
                mvc.perform(json(post("/api/sales"), """
                {"amount":"100","paymentMethod":"CASH","customerId":"%s"}
                """.formatted(juan)).cookie(sid)));
        mvc.perform(get("/api/sales/" + sale).cookie(sid))
                .andExpect(jsonPath("$.customerName").value("Juan"));
        receivables().andExpect(jsonPath("$.total").value("0.00"));
        mvc.perform(get("/api/balance").cookie(sid))
                .andExpect(jsonPath("$.sales").value("100.00"));
    }

    @Test
    void paymentReducesDebtAndCountsAsIncome() throws Exception {
        creditSale(juan, "20000");
        String payment = created(pay(juan, "5000"));
        mvc.perform(get("/api/customer-payments/" + payment).cookie(sid))
                .andExpect(jsonPath("$.type").value("PAYMENT"))
                .andExpect(jsonPath("$.customerName").value("Juan"))
                .andExpect(jsonPath("$.amount").value("5000.00"));
        receivables().andExpect(jsonPath("$.customers[0].debt").value("15000.00"));
        mvc.perform(get("/api/balance").cookie(sid))
                .andExpect(jsonPath("$.payments").value("5000.00"))
                .andExpect(jsonPath("$.balance").value("5000.00"))
                .andExpect(jsonPath("$.receivable").value("15000.00"));
        mvc.perform(get("/api/movements?type=sales").cookie(sid))
                .andExpect(
                        jsonPath("$.items[?(@.type=='PAYMENT')].customerName").value("Juan"));
    }

    @Test
    void paymentCannotExceedDebtOrUseCredit() throws Exception {
        creditSale(juan, "15000");
        pay(juan, "16000")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("amount"))
                .andExpect(jsonPath("$.errors[0].code").value("exceeds_debt"));
        mvc.perform(json(post("/api/customer-payments"), """
                        {"customerId":"%s","amount":"1","paymentMethod":"CREDIT"}
                        """.formatted(juan)).cookie(sid))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("paymentMethod"));
        created(pay(juan, "15000"));
        receivables().andExpect(jsonPath("$.customers.length()").value(0));
    }

    @Test
    void editingAPaymentChecksTheDebtWithoutCountingItself() throws Exception {
        creditSale(juan, "10000");
        String payment = created(pay(juan, "4000"));
        mvc.perform(json(put("/api/customer-payments/" + payment), """
                        {"customerId":"%s","amount":"10000","paymentMethod":"TRANSFER"}
                        """.formatted(juan))
                        .cookie(sid))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paymentMethod").value("TRANSFER"));
        receivables().andExpect(jsonPath("$.total").value("0.00"));
        mvc.perform(json(put("/api/customer-payments/" + payment), """
                        {"customerId":"%s","amount":"10001","paymentMethod":"CASH"}
                        """.formatted(juan))
                        .cookie(sid))
                .andExpect(status().isBadRequest());
        mvc.perform(delete("/api/customer-payments/" + payment).cookie(sid)).andExpect(status().isNoContent());
        receivables().andExpect(jsonPath("$.total").value("10000.00"));
    }

    @Test
    void accountListsCreditSalesAndPaymentsNewestFirst() throws Exception {
        creditSale(juan, "20000");
        clock.set(NOW.plusSeconds(60));
        created(pay(juan, "5000"));
        mvc.perform(get("/api/customers/" + juan + "/account").cookie(sid))
                .andExpect(jsonPath("$.customer.name").value("Juan"))
                .andExpect(jsonPath("$.debt").value("15000.00"))
                .andExpect(jsonPath("$.entries.length()").value(2))
                .andExpect(jsonPath("$.entries[0].type").value("PAYMENT"))
                .andExpect(jsonPath("$.entries[1].type").value("SALE"))
                .andExpect(jsonPath("$.entries[1].amount").value("20000.00"));
    }

    @Test
    void deletingACreditSaleCanLeaveCreditInFavour() throws Exception {
        String sale = creditSale(juan, "20000");
        created(pay(juan, "5000"));
        mvc.perform(delete("/api/sales/" + sale).cookie(sid)).andExpect(status().isNoContent());
        receivables()
                .andExpect(jsonPath("$.customers[0].debt").value("-5000.00"))
                .andExpect(jsonPath("$.total").value("-5000.00"));
    }

    @Test
    void customerWithDebtCannotBeDeletedButOneWithoutCan() throws Exception {
        String sale = creditSale(juan, "1000");
        mvc.perform(delete("/api/customers/" + juan).cookie(sid))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("customer_has_debt"));
        created(pay(juan, "1000"));
        mvc.perform(delete("/api/customers/" + juan).cookie(sid)).andExpect(status().isNoContent());
        mvc.perform(get("/api/sales/" + sale).cookie(sid))
                .andExpect(jsonPath("$.customerName").value("Juan"))
                .andExpect(jsonPath("$.customerId").doesNotExist());
        mvc.perform(get("/api/balance").cookie(sid))
                .andExpect(jsonPath("$.payments").value("1000.00"));
    }

    @Test
    void expensesDoNotAcceptCredit() throws Exception {
        mvc.perform(json(post("/api/expenses"), """
                        {"amount":"10","category":"RENT","paymentMethod":"CREDIT"}
                        """).cookie(sid))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("paymentMethod"));
    }

    @Test
    void anotherBusinessCannotUseOrSeeCustomersAccounts() throws Exception {
        String sale = creditSale(juan, "1000");
        String payment = created(pay(juan, "100"));
        Cookie other = registerOwner();
        mvc.perform(json(post("/api/sales"), """
                        {"amount":"1","paymentMethod":"CREDIT","customerId":"%s"}
                        """.formatted(juan)).cookie(other))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("customer_not_found"));
        mvc.perform(json(post("/api/customer-payments"), """
                        {"customerId":"%s","amount":"1","paymentMethod":"CASH"}
                        """.formatted(juan)).cookie(other))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/customers/" + juan + "/account").cookie(other)).andExpect(status().isNotFound());
        mvc.perform(get("/api/customer-payments/" + payment).cookie(other)).andExpect(status().isNotFound());
        mvc.perform(delete("/api/customer-payments/" + payment).cookie(other)).andExpect(status().isNotFound());
        mvc.perform(get("/api/receivables").cookie(other))
                .andExpect(jsonPath("$.total").value("0.00"));
        receivables().andExpect(jsonPath("$.total").value("900.00"));
        mvc.perform(get("/api/sales/" + sale).cookie(sid)).andExpect(status().isOk());
    }
}
