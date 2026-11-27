package com.ordertoaudit.masters;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.jayway.jsonpath.JsonPath;
import com.ordertoaudit.AbstractLedgerTest;
import com.ordertoaudit.ApiTokens;

/** Recipes (R5.6 to R5.10, M11, M12): versions, copy-from, activation, and a second pack size end to end. */
@AutoConfigureMockMvc
class BomApiTest extends AbstractLedgerTest {

    private static final String BENCHMARK_ACTUALS = """
            {"actualOutput": 188, "lines": [
              {"itemCode": "FLOUR",  "actualQty": 40,  "scrapQty": 0, "uomCode": "kg"},
              {"itemCode": "OIL",    "actualQty": 10.5, "scrapQty": 0, "uomCode": "L"},
              {"itemCode": "SPICE",  "actualQty": 1.2, "scrapQty": 0, "uomCode": "kg"},
              {"itemCode": "POUCH",  "actualQty": 194, "scrapQty": 6, "uomCode": "pc"},
              {"itemCode": "CARTON", "actualQty": 9,   "scrapQty": 0, "uomCode": "pc"}]}""";

    @Autowired
    MockMvc mvc;

    private ResultActions send(String username, String method, String url, String body) throws Exception {
        var request = switch (method) {
            case "POST" -> post(url);
            case "PUT" -> put(url);
            default -> get(url);
        };
        if (body != null) {
            request.contentType(MediaType.APPLICATION_JSON).content(body);
        }
        return mvc.perform(request.with(ApiTokens.as(mvc, username)));
    }

    private ResultActions admin(String method, String url, String body) throws Exception {
        return send("admin", method, url, body);
    }

    private static long id(ResultActions result) throws Exception {
        return ((Number) JsonPath.read(result.andReturn().getResponse().getContentAsString(), "$.id")).longValue();
    }

    private long postedRun(String productCode, String actuals) throws Exception {
        long run = id(send("production", "POST", "/api/production-runs",
                "{\"productCode\": \"%s\", \"runDate\": \"2026-11-28\", \"batches\": 1}".formatted(productCode))
                .andExpect(status().isCreated()));
        send("production", "PUT", "/api/production-runs/" + run + "/actuals", actuals).andExpect(status().isOk());
        send("production", "POST", "/api/production-runs/" + run + "/post", null).andExpect(status().isOk());
        return run;
    }

    /** The 500 g pack: its own pouch, and a product sold in cartons of 10. */
    private void create500gItems() throws Exception {
        admin("POST", "/api/items", """
                {"code": "POUCH500", "name": "500 g pouch", "kind": "PACKAGING", "baseUnit": "pc", "reorderLevel": 100}""")
                .andExpect(status().isCreated());
        admin("POST", "/api/items", """
                {"code": "MURUKKU500", "name": "Murukku 500 g", "kind": "FG", "baseUnit": "pkt", "hsnCode": "21069099",
                 "gstRate": 5, "sellingPrice": 110, "packWeightKg": 0.5, "packetsPerCarton": 10}""")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.units", contains("pkt", "ctn")));
    }

    private static final String RECIPE_500G = """
            {"productCode": "MURUKKU500", "outputQty": 80, "copyIngredientsFrom": "MURUKKU200",
             "lines": [{"itemCode": "POUCH500", "qty": 80}, {"itemCode": "CARTON", "qty": 8, "uomCode": "pc"}]}""";

    @Test
    void aNewVersionReplacesTheActiveOneAndLeavesAPostedRunAlone() throws Exception {
        long run = postedRun("MURUKKU200", BENCHMARK_ACTUALS);

        // Flour entered in grams is stored in kg (R5.9)
        long v2 = id(admin("POST", "/api/boms", """
                {"productCode": "MURUKKU200", "outputQty": 200, "lines": [
                  {"itemCode": "FLOUR", "qty": 45000, "uomCode": "g"}, {"itemCode": "OIL", "qty": 10},
                  {"itemCode": "SPICE", "qty": 1.2}, {"itemCode": "POUCH", "qty": 200}, {"itemCode": "CARTON", "qty": 10}]}""")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.version").value(2))
                .andExpect(jsonPath("$.active").value(false))          // saved, not yet in use
                .andExpect(jsonPath("$.lines[0].qty").value(45.0))
                .andExpect(jsonPath("$.lines[0].unit").value("kg"))
                .andExpect(jsonPath("$.lines[3].lineKind").value("PACKAGING")));
        send("production", "POST", "/api/production-runs",
                "{\"productCode\": \"MURUKKU200\", \"runDate\": \"2026-11-28\", \"batches\": 1}")
                .andExpect(jsonPath("$.lines[?(@.itemCode=='FLOUR')].plannedQty", contains(40.0)));   // still v1

        admin("POST", "/api/boms/" + v2 + "/activate", null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(true));
        admin("POST", "/api/boms/" + v2 + "/activate", null).andExpect(status().isConflict());

        send("production", "GET", "/api/boms?productCode=MURUKKU200", null)
                .andExpect(jsonPath("$[*].version", contains(2, 1)))
                .andExpect(jsonPath("$[*].active", contains(true, false)));
        List<Map<String, Object>> audits = jdbc.queryForList("SELECT action, before_data::text AS before, "
                + "after_data::text AS after FROM audit_log WHERE entity_type = 'BOM' AND action = 'UPDATE' ORDER BY id");
        assertThat(audits).hasSize(2);
        assertThat((String) audits.get(0).get("after")).contains("\"version\": 1", "\"active\": false");
        assertThat((String) audits.get(1).get("after")).contains("\"version\": 2", "\"active\": true");

        // R8.5: the posted run keeps its own copied standards; a new run takes version 2
        send("production", "GET", "/api/production-runs/" + run, null)
                .andExpect(jsonPath("$.lines[?(@.itemCode=='FLOUR')].plannedQty", contains(40.0)))
                .andExpect(jsonPath("$.lines[?(@.itemCode=='OIL')].flagged", contains(true)));
        send("production", "POST", "/api/production-runs",
                "{\"productCode\": \"MURUKKU200\", \"runDate\": \"2026-11-28\", \"batches\": 1}")
                .andExpect(jsonPath("$.lines[?(@.itemCode=='FLOUR')].plannedQty", contains(45.0)));
    }

    @Test
    void copyingTakesTheSameIngredientLinesAndTheNewPacksOwnPackaging() throws Exception {
        create500gItems();

        String created = admin("POST", "/api/boms", RECIPE_500G)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.version").value(1))
                .andExpect(jsonPath("$.lines[*].itemCode", contains("FLOUR", "OIL", "SPICE", "POUCH500", "CARTON")))
                .andReturn().getResponse().getContentAsString();
        String source = admin("GET", "/api/boms?productCode=MURUKKU200", null).andReturn().getResponse().getContentAsString();

        List<Map<String, Object>> copied = JsonPath.read(created, "$.lines[?(@.lineKind=='INGREDIENT')]");
        List<Map<String, Object>> original = JsonPath.read(source, "$[0].lines[?(@.lineKind=='INGREDIENT')]");
        assertThat(copied).extracting(line -> line.get("itemCode") + " " + line.get("qty"))
                .containsExactlyElementsOf(original.stream().map(line -> line.get("itemCode") + " " + line.get("qty")).toList())
                .containsExactly("FLOUR 40.0", "OIL 10.0", "SPICE 1.2");
    }

    @Test
    void recipeLinesAreCheckedAndAnItemInARecipeKeepsItsUnit() throws Exception {
        create500gItems();
        admin("POST", "/api/boms", RECIPE_500G.replace("\"POUCH500\", \"qty\": 80}", "\"POUCH500\", \"qty\": 80}, {\"itemCode\": \"FLOUR\", \"qty\": 1}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("FLOUR appears twice in the recipe (copied lines included)"));
        admin("POST", "/api/boms", RECIPE_500G.replace("\"POUCH500\"", "\"MURUKKU200\""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("is a finished good")));
        admin("POST", "/api/boms", RECIPE_500G.replace("\"MURUKKU200\"", "\"MURUKKU500\""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("MURUKKU500 has no active recipe to copy from"));
        admin("POST", "/api/boms", RECIPE_500G.replace(", \"copyIngredientsFrom\": \"MURUKKU200\"", ""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("at least one raw-material")));
        admin("POST", "/api/boms", RECIPE_500G.replace("\"outputQty\": 80", "\"outputQty\": 80.5"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Packets per batch must be a whole number"));
        admin("POST", "/api/boms", RECIPE_500G.replace("\"qty\": 80}", "\"qty\": 80, \"uomCode\": \"pkt\"}"))
                .andExpect(status().isBadRequest());   // a pouch is counted in pieces (R3.3)
        assertThat(jdbc.queryForObject("SELECT count(*) FROM bom", Integer.class)).isEqualTo(1);

        // M7: in a recipe (even an inactive version), the pouch keeps its unit
        admin("POST", "/api/boms", RECIPE_500G).andExpect(status().isCreated());
        admin("PUT", "/api/items/POUCH500", "{\"name\": \"500 g pouch\", \"baseUnit\": \"pkt\"}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(
                        "The base unit stays pc: POUCH500 is in a recipe, and recipe quantities are in pc"));
    }

    @Test
    void aSecondPackSizeIsProducedAndSoldEndToEnd() throws Exception {
        create500gItems();
        long recipe = id(admin("POST", "/api/boms", RECIPE_500G).andExpect(status().isCreated()));
        admin("POST", "/api/boms/" + recipe + "/activate", null).andExpect(status().isOk());

        send("production", "POST", "/api/purchases", """
                {"supplierId": %d, "purchaseDate": "2026-11-27", "billNo": "P500-1",
                 "lines": [{"itemCode": "POUCH500", "qty": 80, "uomCode": "pc", "rate": 1.5}]}""".formatted(supplierId()))
                .andExpect(status().isCreated());

        long run = postedRun("MURUKKU500", """
                {"actualOutput": 80, "lines": [
                  {"itemCode": "FLOUR",    "actualQty": 40,  "scrapQty": 0, "uomCode": "kg"},
                  {"itemCode": "OIL",      "actualQty": 10,  "scrapQty": 0, "uomCode": "L"},
                  {"itemCode": "SPICE",    "actualQty": 1.2, "scrapQty": 0, "uomCode": "kg"},
                  {"itemCode": "POUCH500", "actualQty": 80,  "scrapQty": 0, "uomCode": "pc"},
                  {"itemCode": "CARTON",   "actualQty": 8,   "scrapQty": 0, "uomCode": "pc"}]}""");
        send("production", "GET", "/api/production-runs/" + run, null)
                .andExpect(jsonPath("$.status").value("POSTED"))
                .andExpect(jsonPath("$.yieldFlagged").value(false));

        // 2 cartons of 10 at the 500 g price
        long retailerA = jdbc.queryForObject("SELECT id FROM customer WHERE code = 'RET-A'", Long.class);
        long order = id(send("salesman_a", "POST", "/api/orders", """
                {"customerId": %d, "clientUuid": "%s", "lines": [{"itemCode": "MURUKKU500", "qty": 2, "uomCode": "ctn"}]}
                """.formatted(retailerA, UUID.randomUUID()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.lines[0].qty").value(20.0))
                .andExpect(jsonPath("$.lines[0].rate").value(110.0)));
        admin("POST", "/api/orders/" + order + "/approve", null).andExpect(jsonPath("$.status").value("APPROVED"));
        long invoice = id(admin("POST", "/api/invoices", "{\"orderId\": %d, \"invoiceDate\": \"2026-11-28\"}".formatted(order))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.taxableValue").value(2200.0))
                .andExpect(jsonPath("$.cgst").value(55.0))
                .andExpect(jsonPath("$.sgst").value(55.0))
                .andExpect(jsonPath("$.total").value(2310.0)));
        admin("POST", "/api/dispatches", "{\"invoiceId\": %d, \"dispatchDate\": \"2026-11-28\"}".formatted(invoice))
                .andExpect(status().isCreated());

        // The order links to its invoice; 80 made, 20 sent; the 200 g pack is untouched
        admin("GET", "/api/orders/" + order, null)
                .andExpect(jsonPath("$.invoiceId").value((int) invoice))
                .andExpect(jsonPath("$.status").value("DISPATCHED"));
        admin("GET", "/api/inventory/balances", null)
                .andExpect(jsonPath("$[?(@.itemCode=='MURUKKU500')].qtyOnHand", contains(60.0)))
                .andExpect(jsonPath("$[?(@.itemCode=='MURUKKU200')].qtyOnHand", contains(50.0)))
                .andExpect(jsonPath("$[?(@.itemCode=='POUCH500')].qtyOnHand", contains(0.0)));
        send("production", "GET", "/api/boms?productCode=MURUKKU200", null)
                .andExpect(jsonPath("$[*].version", contains(1)))
                .andExpect(jsonPath("$[0].active").value(true));
    }
}
