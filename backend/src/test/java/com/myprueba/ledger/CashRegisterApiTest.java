package com.myprueba.ledger;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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

/** Cash register: open with a float, expected cash in real time, close with a count. */
class CashRegisterApiTest extends ApiTest {

    static final Instant NOW = Instant.parse("2026-10-28T13:00:00Z");

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

    void record(String path, String body) throws Exception {
        mvc.perform(json(post(path), body).cookie(sid)).andExpect(status().isCreated());
    }

    @Test
    void expectedCashFollowsCashMovementsSinceOpening() throws Exception {
        // Before opening: not counted.
        record("/api/sales", """
                {"amount":"99000","paymentMethod":"CASH"}
                """);
        mvc.perform(get("/api/cash-register").cookie(sid))
                .andExpect(jsonPath("$.open").doesNotExist());
        clock.set(NOW.plus(Duration.ofMinutes(5)));
        mvc.perform(json(post("/api/cash-register/open"), """
                        {"openingAmount":"50000"}
                        """).cookie(sid))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.opening").value("50000.00"));
        clock.set(NOW.plus(Duration.ofMinutes(10)));
        String juan = JsonPath.read(
                mvc.perform(json(post("/api/customers"), """
                                {"name":"Juan"}
                                """).cookie(sid))
                        .andReturn()
                        .getResponse()
                        .getContentAsString(),
                "$.id");
        record("/api/sales", """
                {"amount":"6900","paymentMethod":"CASH"}
                """);
        record("/api/sales", """
                {"amount":"10000","paymentMethod":"CARD"}
                """);
        record("/api/sales", """
                {"amount":"8000","status":"CREDIT","customerId":"%s"}
                """.formatted(juan));
        record("/api/customer-payments", """
                {"customerId":"%s","amount":"5000","paymentMethod":"CASH"}
                """.formatted(juan));
        record("/api/expenses", """
                {"amount":"2000","category":"TRANSPORT","paymentMethod":"CASH"}
                """);
        record("/api/expenses", """
                {"amount":"7000","category":"RENT","paymentMethod":"TRANSFER"}
                """);
        mvc.perform(get("/api/cash-register").cookie(sid))
                .andExpect(jsonPath("$.open.cashSales").value("6900.00"))
                .andExpect(jsonPath("$.open.cashPayments").value("5000.00"))
                .andExpect(jsonPath("$.open.cashExpenses").value("2000.00"))
                .andExpect(jsonPath("$.open.cashSupplierPayments").value("0.00"))
                .andExpect(jsonPath("$.open.expected").value("59900.00"));

        clock.set(NOW.plus(Duration.ofHours(9)));
        mvc.perform(json(post("/api/cash-register/close"), """
                        {"countedAmount":"59000","note":"Faltan monedas"}
                        """).cookie(sid))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.expected").value("59900.00"))
                .andExpect(jsonPath("$.counted").value("59000.00"))
                .andExpect(jsonPath("$.difference").value("-900.00"))
                .andExpect(jsonPath("$.closedAt").value("2026-10-28T22:00:00Z"));
        mvc.perform(get("/api/cash-register").cookie(sid))
                .andExpect(jsonPath("$.open").doesNotExist())
                .andExpect(jsonPath("$.last.difference").value("-900.00"));
        mvc.perform(get("/api/cash-register/history").cookie(sid))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].note").value("Faltan monedas"));
    }

    @Test
    void onlyOneRegisterIsOpenAtATime() throws Exception {
        mvc.perform(json(post("/api/cash-register/close"), """
                        {"countedAmount":"0"}
                        """).cookie(sid))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("cash_register_closed"));
        mvc.perform(json(post("/api/cash-register/open"), """
                        {"openingAmount":"0"}
                        """).cookie(sid)).andExpect(status().isCreated());
        mvc.perform(json(post("/api/cash-register/open"), """
                        {"openingAmount":"1000"}
                        """).cookie(sid))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("cash_register_open"));
        mvc.perform(json(post("/api/cash-register/close"), """
                        {"countedAmount":"-5"}
                        """).cookie(sid))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("countedAmount"));
    }

    @Test
    void registersAreKeptPerBusiness() throws Exception {
        mvc.perform(json(post("/api/cash-register/open"), """
                        {"openingAmount":"1000"}
                        """).cookie(sid)).andExpect(status().isCreated());
        Cookie other = registerOwner();
        mvc.perform(get("/api/cash-register").cookie(other))
                .andExpect(jsonPath("$.open").doesNotExist());
        mvc.perform(json(post("/api/sales"), """
                        {"amount":"5000","paymentMethod":"CASH"}
                        """).cookie(other)).andExpect(status().isCreated());
        mvc.perform(get("/api/cash-register").cookie(sid))
                .andExpect(jsonPath("$.open.expected").value("1000.00"));
        mvc.perform(json(post("/api/cash-register/close"), """
                        {"countedAmount":"0"}
                        """).cookie(other)).andExpect(status().isConflict());
    }
}
