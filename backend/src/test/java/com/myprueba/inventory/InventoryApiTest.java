package com.myprueba.inventory;

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
import net.minidev.json.JSONArray;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.ResultActions;

class InventoryApiTest extends ApiTest {

    Cookie sid;

    @BeforeEach
    void setUp() throws Exception {
        sid = registerOwner();
    }

    String created(ResultActions result) throws Exception {
        return JsonPath.read(
                result.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.id");
    }

    static String quoted(String value) {
        return value == null ? "null" : "\"" + value + "\"";
    }

    String product(String name, String price, String cost, String stock, String min) throws Exception {
        return created(mvc.perform(
                json(post("/api/products"), """
                {"name":"%s","price":"%s","cost":%s,"stock":%s,"minStock":%s}
                """.formatted(name, price, quoted(cost), quoted(stock), quoted(min)))
                        .cookie(sid)));
    }

    String sell(String productId, String quantity) throws Exception {
        return created(mvc.perform(
                json(post("/api/sales"), """
                {"items":[{"productId":"%s","quantity":"%s"}],"paymentMethod":"CASH"}
                """.formatted(productId, quantity)).cookie(sid)));
    }

    String stockOf(String productId) throws Exception {
        String body = mvc.perform(get("/api/inventory").cookie(sid))
                .andReturn()
                .getResponse()
                .getContentAsString();
        JSONArray stock = JsonPath.read(body, "$.items[?(@.productId=='%s')].stock".formatted(productId));
        return (String) stock.get(0);
    }

    ResultActions adjust(String productId, String type, String quantity) throws Exception {
        return mvc.perform(json(post("/api/inventory/" + productId + "/adjustments"), """
                        {"type":"%s","quantity":"%s","note":"nota"}
                        """.formatted(type, quantity))
                        .cookie(sid))
                .andExpect(status().isCreated());
    }

    @Test
    void productsCarryAnOptionalCost() throws Exception {
        String id = product("Gaseosa", "3000", "2100.5", null, null);
        mvc.perform(get("/api/products").cookie(sid))
                .andExpect(jsonPath("$[0].cost").value("2100.50"));
        mvc.perform(json(put("/api/products/" + id), """
                        {"name":"Gaseosa","price":"3000"}
                        """).cookie(sid))
                .andExpect(jsonPath("$.cost").doesNotExist());
        mvc.perform(json(post("/api/products"), """
                        {"name":"Malo","price":"1","cost":"0"}
                        """).cookie(sid))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("cost"));
    }

    @Test
    void productsStartWithTheirInitialStock() throws Exception {
        String gaseosa = product("Gaseosa", "3000", null, "10", "2");
        String pan = product("Pan", "500", null, null, null);
        assertThat(stockOf(gaseosa)).isEqualTo("10");
        assertThat(stockOf(pan)).isEqualTo("0");
        mvc.perform(get("/api/inventory/" + gaseosa + "/movements").cookie(sid))
                .andExpect(jsonPath("$[0].type").value("INITIAL"))
                .andExpect(jsonPath("$[0].change").value("10"));
    }

    @Test
    void saleLowersStockAndRecordsMovement() throws Exception {
        String gaseosa = product("Gaseosa", "3000", null, "10", null);
        sell(gaseosa, "2");
        assertThat(stockOf(gaseosa)).isEqualTo("8");
        mvc.perform(get("/api/inventory/" + gaseosa + "/movements").cookie(sid))
                .andExpect(jsonPath("$[0].type").value("SALE"))
                .andExpect(jsonPath("$[0].change").value("-2"))
                .andExpect(jsonPath("$[0].stockAfter").value("8"))
                .andExpect(jsonPath("$[1].type").value("INITIAL"));
    }

    @Test
    void editingAppliesTheDifferenceAndDeletingRestores() throws Exception {
        String gaseosa = product("Gaseosa", "3000", null, "10", null);
        String sale = sell(gaseosa, "2");
        mvc.perform(json(put("/api/sales/" + sale), """
                        {"items":[{"productId":"%s","quantity":"5"}],"paymentMethod":"CASH"}
                        """.formatted(gaseosa)).cookie(sid))
                .andExpect(status().isOk());
        assertThat(stockOf(gaseosa)).isEqualTo("5");
        mvc.perform(get("/api/inventory/" + gaseosa + "/movements").cookie(sid))
                .andExpect(jsonPath("$[0].type").value("SALE_EDIT"))
                .andExpect(jsonPath("$[0].change").value("-3"));
        mvc.perform(delete("/api/sales/" + sale).cookie(sid)).andExpect(status().isNoContent());
        assertThat(stockOf(gaseosa)).isEqualTo("10");
        mvc.perform(get("/api/inventory/" + gaseosa + "/movements").cookie(sid))
                .andExpect(jsonPath("$[0].type").value("SALE_DELETE"))
                .andExpect(jsonPath("$[0].change").value("5"));
    }

    @Test
    void switchingProductInASaleMovesStockBetweenThem() throws Exception {
        String gaseosa = product("Gaseosa", "3000", null, "10", null);
        String pan = product("Pan", "500", null, "20", null);
        String sale = sell(gaseosa, "2");
        mvc.perform(json(put("/api/sales/" + sale), """
                        {"items":[{"productId":"%s","quantity":"4"}],"paymentMethod":"CASH"}
                        """.formatted(pan)).cookie(sid))
                .andExpect(status().isOk());
        assertThat(stockOf(gaseosa)).isEqualTo("10");
        assertThat(stockOf(pan)).isEqualTo("16");
    }

    @Test
    void freeSalesDoNotMoveStock() throws Exception {
        String pan = product("Pan", "500", null, "3", null);
        mvc.perform(json(post("/api/sales"), """
                        {"amount":"100","paymentMethod":"CASH"}
                        """).cookie(sid)).andExpect(status().isCreated());
        assertThat(stockOf(pan)).isEqualTo("3");
    }

    @Test
    void purchaseLossAndCountAdjustStock() throws Exception {
        String gaseosa = product("Gaseosa", "3000", null, "8", null);
        adjust(gaseosa, "PURCHASE", "12").andExpect(jsonPath("$.stock").value("20"));
        adjust(gaseosa, "LOSS", "1").andExpect(jsonPath("$.stock").value("19"));
        adjust(gaseosa, "COUNT", "15").andExpect(jsonPath("$.stock").value("15"));
        mvc.perform(get("/api/inventory/" + gaseosa + "/movements").cookie(sid))
                .andExpect(jsonPath("$[0].type").value("COUNT"))
                .andExpect(jsonPath("$[0].change").value("-4"))
                .andExpect(jsonPath("$[0].note").value("nota"));
        adjust(gaseosa, "COUNT", "0").andExpect(jsonPath("$.stock").value("0"));
    }

    @Test
    void minimumStockCanBeChangedLater() throws Exception {
        String pan = product("Pan", "500", null, "4", null);
        mvc.perform(json(put("/api/inventory/" + pan), """
                        {"minStock":"5"}
                        """).cookie(sid))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.minStock").value("5"))
                .andExpect(jsonPath("$.low").value(true));
    }

    @Test
    void invalidAdjustmentsAndInitialStockAreRejected() throws Exception {
        String gaseosa = product("Gaseosa", "3000", null, "5", null);
        for (String[] bad : new String[][] {{"PURCHASE", "0"}, {"LOSS", "0"}, {"COUNT", "-1"}, {"COUNT", "x"}}) {
            mvc.perform(json(post("/api/inventory/" + gaseosa + "/adjustments"), """
                            {"type":"%s","quantity":"%s"}
                            """.formatted(bad[0], bad[1]))
                            .cookie(sid))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors[0].field").value("quantity"));
        }
        mvc.perform(json(post("/api/inventory/" + gaseosa + "/adjustments"), """
                        {"type":"STEAL","quantity":"1"}
                        """)
                        .cookie(sid))
                .andExpect(jsonPath("$.errors[0].field").value("type"));
        mvc.perform(json(post("/api/products"), """
                        {"name":"Malo","price":"1","stock":"-1","minStock":"x"}
                        """).cookie(sid))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[?(@.field=='stock')].code").value("invalid_format"))
                .andExpect(jsonPath("$.errors[?(@.field=='minStock')].code").value("invalid_format"));
    }

    @Test
    void lowAndNegativeStockAreFlaggedAndSummarized() throws Exception {
        product("Gaseosa", "3000", "2500", "4", "5");
        product("Pan", "500", null, "2", null);
        String arroz = product("Arroz", "4200", "3000", "1", null);
        sell(arroz, "3");
        mvc.perform(get("/api/inventory").cookie(sid))
                .andExpect(jsonPath("$.summary.references").value(3))
                .andExpect(jsonPath("$.summary.lowStock").value(2))
                .andExpect(jsonPath("$.summary.value").value("10000.00"))
                .andExpect(jsonPath("$.items[?(@.name=='Arroz')].stock").value("-2"))
                .andExpect(jsonPath("$.items[?(@.name=='Arroz')].low").value(true))
                .andExpect(jsonPath("$.items[?(@.name=='Pan')].low").value(false));
        mvc.perform(get("/api/inventory?lowOnly=true").cookie(sid))
                .andExpect(jsonPath("$.items.length()").value(2));
    }

    @Test
    void stockChangesRollBackWithAFailedSale() throws Exception {
        String gaseosa = product("Gaseosa", "3000", null, "10", null);
        mvc.perform(json(post("/api/sales"), """
                        {"items":[{"productId":"%s","quantity":"2"},{"productId":"00000000-0000-0000-0000-000000000000","quantity":"1"}],"paymentMethod":"CASH"}
                        """.formatted(gaseosa)).cookie(sid))
                .andExpect(status().isNotFound());
        assertThat(stockOf(gaseosa)).isEqualTo("10");
    }

    @Test
    void sellingWithoutStockCanBeBlocked() throws Exception {
        String gaseosa = product("Gaseosa", "3000", null, "2", null);
        mvc.perform(get("/api/business/settings").cookie(sid))
                .andExpect(jsonPath("$.allowNegativeStock").value(true));
        mvc.perform(json(put("/api/business/settings"), """
                        {"allowNegativeStock":false}
                        """).cookie(sid))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.allowNegativeStock").value(false));
        mvc.perform(json(post("/api/sales"), """
                        {"items":[{"productId":"%s","quantity":"3"}],"paymentMethod":"CASH"}
                        """.formatted(gaseosa)).cookie(sid))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("insufficient_stock"))
                .andExpect(jsonPath("$.productName").value("Gaseosa"))
                .andExpect(jsonPath("$.available").value("2"));
        assertThat(stockOf(gaseosa)).isEqualTo("2");
        mvc.perform(get("/api/movements").cookie(sid))
                .andExpect(jsonPath("$.items.length()").value(0));

        // An edit is checked only against what it adds: keeping 2 units is fine.
        String sale = sell(gaseosa, "2");
        mvc.perform(json(put("/api/sales/" + sale), """
                        {"items":[{"productId":"%s","quantity":"2"}],"paymentMethod":"CARD"}
                        """.formatted(gaseosa)).cookie(sid))
                .andExpect(status().isOk());

        mvc.perform(json(put("/api/business/settings"), """
                        {"allowNegativeStock":true}
                        """).cookie(sid)).andExpect(status().isOk());
        sell(gaseosa, "1");
        assertThat(stockOf(gaseosa)).isEqualTo("-1");
        mvc.perform(json(put("/api/business/settings"), "{}").cookie(sid)).andExpect(status().isBadRequest());
    }

    @Test
    void anotherBusinessCannotSeeOrChangeInventory() throws Exception {
        String gaseosa = product("Gaseosa", "3000", null, "10", null);
        Cookie other = registerOwner();
        mvc.perform(get("/api/inventory").cookie(other))
                .andExpect(jsonPath("$.items.length()").value(0));
        mvc.perform(json(put("/api/inventory/" + gaseosa), """
                        {"minStock":"999"}
                        """).cookie(other)).andExpect(status().isNotFound());
        mvc.perform(json(post("/api/inventory/" + gaseosa + "/adjustments"), """
                        {"type":"LOSS","quantity":"10"}
                        """)
                        .cookie(other))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/inventory/" + gaseosa + "/movements").cookie(other))
                .andExpect(status().isNotFound());
        assertThat(stockOf(gaseosa)).isEqualTo("10");
    }
}
