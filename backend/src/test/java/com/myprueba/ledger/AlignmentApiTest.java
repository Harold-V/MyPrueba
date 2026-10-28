package com.myprueba.ledger;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.myprueba.support.ApiTest;
import jakarta.servlet.http.Cookie;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.ResultActions;

/** Phase 1: behaviour aligned with what REA verified in Treinta. */
class AlignmentApiTest extends ApiTest {

    // 2026-10-28 03:14:23 in Bogotá
    static final Instant NOW = Instant.parse("2026-10-28T08:14:23Z");

    Cookie sid;
    String juan;

    @BeforeEach
    void setUp() throws Exception {
        clock.set(NOW);
        sid = registerOwner();
        juan = created(mvc.perform(json(post("/api/customers"), """
                {"name":"Juan"}
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

    @Test
    void colombianPaymentMethodsAreAccepted() throws Exception {
        for (String method : new String[] {"NEQUI", "DAVIPLATA", "PAYMENT_LINK"}) {
            String id = created(
                    mvc.perform(json(post("/api/sales"), """
                    {"amount":"1000","paymentMethod":"%s"}
                    """.formatted(method)).cookie(sid)));
            mvc.perform(get("/api/sales/" + id).cookie(sid))
                    .andExpect(jsonPath("$.paymentMethod").value(method))
                    .andExpect(jsonPath("$.status").value("PAID"));
        }
        mvc.perform(json(post("/api/expenses"), """
                        {"amount":"10","category":"RENT","paymentMethod":"NEQUI"}
                        """).cookie(sid)).andExpect(status().isCreated());
    }

    @Test
    void creditIsAStatusWithOptionalDueDate() throws Exception {
        mvc.perform(json(post("/api/sales"), """
                        {"amount":"1000","status":"CREDIT"}
                        """).cookie(sid))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("customerId"));
        String id = created(
                mvc.perform(json(post("/api/sales"), """
                {"amount":"20000","status":"CREDIT","customerId":"%s","dueDate":"2026-11-15"}
                """.formatted(juan)).cookie(sid)));
        mvc.perform(get("/api/sales/" + id).cookie(sid))
                .andExpect(jsonPath("$.status").value("CREDIT"))
                .andExpect(jsonPath("$.dueDate").value("2026-11-15"));
        mvc.perform(get("/api/receivables").cookie(sid))
                .andExpect(jsonPath("$.customers[0].debt").value("20000.00"))
                .andExpect(jsonPath("$.customers[0].overdue").value(false));
    }

    @Test
    void paidSalesNeedAPaymentMethod() throws Exception {
        mvc.perform(json(post("/api/sales"), """
                        {"amount":"1000","status":"PAID"}
                        """).cookie(sid))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("paymentMethod"));
    }

    @Test
    void dueDateCannotPrecedeTheSale() throws Exception {
        mvc.perform(json(post("/api/sales"), """
                        {"amount":"1000","status":"CREDIT","customerId":"%s","dueDate":"2026-10-27"}
                        """.formatted(juan)).cookie(sid))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("dueDate"))
                .andExpect(jsonPath("$.errors[0].code").value("before_sale"));
    }

    @Test
    void receivablesShowSinceWhenAndOverdueDebts() throws Exception {
        created(mvc.perform(json(post("/api/sales"), """
                {"amount":"10000","status":"CREDIT","customerId":"%s","dueDate":"2026-10-30","occurredAt":"2026-10-20T15:00:00Z"}
                """.formatted(juan)).cookie(sid)));
        clock.set(NOW.plus(Duration.ofDays(1)));
        created(mvc.perform(json(post("/api/sales"), """
                {"amount":"5000","status":"CREDIT","customerId":"%s"}
                """.formatted(juan)).cookie(sid)));
        mvc.perform(get("/api/receivables").cookie(sid))
                .andExpect(jsonPath("$.customers[0].oldestDebtAt").value("2026-10-20T15:00:00Z"))
                .andExpect(jsonPath("$.customers[0].overdue").value(false));

        // A payment settles the oldest sale first: the debt now dates from the second sale.
        created(mvc.perform(
                json(post("/api/customer-payments"), """
                {"customerId":"%s","amount":"10000","paymentMethod":"CASH"}
                """.formatted(juan)).cookie(sid)));
        mvc.perform(get("/api/receivables").cookie(sid))
                .andExpect(jsonPath("$.customers[0].oldestDebtAt").value("2026-10-29T08:14:23Z"));

        // Unpaid and past its due date → overdue.
        created(mvc.perform(json(post("/api/sales"), """
                {"amount":"1000","status":"CREDIT","customerId":"%s","dueDate":"2026-10-29"}
                """.formatted(juan)).cookie(sid)));
        clock.set(NOW.plus(Duration.ofDays(3)));
        mvc.perform(get("/api/receivables").cookie(sid))
                .andExpect(jsonPath("$.customers[0].overdue").value(true));
    }

    @Test
    void expensesWithoutConceptAreNamedAfterTheirMoment() throws Exception {
        String id = created(mvc.perform(json(post("/api/expenses"), """
                {"amount":"1000","category":"UTILITIES","paymentMethod":"CASH"}
                """).cookie(sid)));
        mvc.perform(get("/api/expenses/" + id).cookie(sid))
                .andExpect(jsonPath("$.concept").value("Gasto 281026031423"));
    }

    @Test
    void expenseCategoriesMatchTreinta() throws Exception {
        for (String category : new String[] {"ADMINISTRATIVE", "ADVERTISING", "EQUIPMENT"}) {
            mvc.perform(json(post("/api/expenses"), """
                            {"amount":"10","category":"%s","paymentMethod":"CASH"}
                            """.formatted(category)).cookie(sid))
                    .andExpect(status().isCreated());
        }
        mvc.perform(json(post("/api/expenses"), """
                        {"amount":"10","category":"TAXES","paymentMethod":"CASH"}
                        """).cookie(sid))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("category"));
    }

    @Test
    void businessTypesMatchTreinta() throws Exception {
        mvc.perform(json(put("/api/business"), """
                        {"name":"Ferre","category":"HARDWARE_STORE"}
                        """).cookie(sid))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.category").value("HARDWARE_STORE"));
        mvc.perform(json(put("/api/business"), """
                        {"name":"Ferre","category":"GROCERY"}
                        """).cookie(sid)).andExpect(status().isBadRequest());
    }
}
