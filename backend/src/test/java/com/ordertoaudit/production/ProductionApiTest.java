package com.ordertoaudit.production;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import com.jayway.jsonpath.JsonPath;
import com.ordertoaudit.AbstractLedgerTest;

/** The seven production endpoints and their 400 / 404 / 409 answers. */
@AutoConfigureMockMvc
class ProductionApiTest extends AbstractLedgerTest {

    private static final String BENCHMARK_ACTUALS = """
            {"actualOutput": 188, "lines": [
              {"itemCode": "FLOUR",  "actualQty": 40,    "scrapQty": 0, "uomCode": "kg"},
              {"itemCode": "OIL",    "actualQty": 10500, "scrapQty": 0, "uomCode": "ml"},
              {"itemCode": "SPICE",  "actualQty": 1.2,   "scrapQty": 0, "uomCode": "kg"},
              {"itemCode": "POUCH",  "actualQty": 194,   "scrapQty": 6, "uomCode": "pc"},
              {"itemCode": "CARTON", "actualQty": 9,     "scrapQty": 0, "uomCode": "pc"}]}
            """;

    @Autowired
    MockMvc mvc;

    private long createRun() throws Exception {
        String body = mvc.perform(post("/api/production-runs").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productCode\": \"MURUKKU200\", \"runDate\": \"2026-11-03\", \"batches\": 1}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.id")).longValue();
    }

    private void enterBenchmarkActuals(long id) throws Exception {
        mvc.perform(put("/api/production-runs/" + id + "/actuals").contentType(MediaType.APPLICATION_JSON)
                        .content(BENCHMARK_ACTUALS))
                .andExpect(status().isOk());
    }

    @Test
    void wholeBenchmarkRunThroughTheEndpoints() throws Exception {
        mvc.perform(post("/api/production-runs").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productCode\": \"MURUKKU200\", \"runDate\": \"2026-11-03\", \"batches\": 1}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.runNo").value("RUN/2026-27/0001"))
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.plannedOutput").value(200.0))
                .andExpect(jsonPath("$.costing").doesNotExist());
        long id = jdbc.queryForObject("SELECT id FROM production_run", Long.class);

        enterBenchmarkActuals(id);

        mvc.perform(post("/api/production-runs/" + id + "/post"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("POSTED"))
                .andExpect(jsonPath("$.postedBy").value("admin"))
                .andExpect(jsonPath("$.yieldVariancePct").value(-6.0))
                .andExpect(jsonPath("$.yieldFlagged").value(true))
                .andExpect(jsonPath("$.lines[?(@.itemCode=='OIL')].variancePct").value(5.0))
                .andExpect(jsonPath("$.lines[?(@.itemCode=='OIL')].flagged").value(true))
                .andExpect(jsonPath("$.lines[?(@.itemCode=='FLOUR')].flagged").value(false))
                // No 60 kg purchase in this test, so flour costs 42: 40 x 42 + 1470 + 240 = 3390
                .andExpect(jsonPath("$.costing.materialCost").value(3390.0))
                .andExpect(jsonPath("$.costing.packagingCost").value(620.0));
    }

    @Test
    void overheadsAndTheReportAndTheList() throws Exception {
        long id = createRun();
        enterBenchmarkActuals(id);
        mvc.perform(post("/api/production-runs/" + id + "/post")).andExpect(status().isOk());

        mvc.perform(post("/api/production-runs/" + id + "/overheads").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\": \"LABOUR\", \"amount\": 1200, \"note\": \"Labour\"}"))
                .andExpect(status().isCreated());
        mvc.perform(post("/api/production-runs/" + id + "/overheads").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\": \"POWER_GAS\", \"amount\": 600}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.costing.overheadTotal").value(1800.0))
                .andExpect(jsonPath("$.costing.totalCost").value(5810.0))             // 3390 + 620 + 1800
                .andExpect(jsonPath("$.costing.costPerPacketRounded").value(30.9));   // 5810 / 188 = 30.9043

        mvc.perform(get("/api/production-runs/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.overheads", hasSize(2)))
                .andExpect(jsonPath("$.costing.marginPerPacketRounded").value(19.1))   // 50 - 30.9043 = 19.0957
                .andExpect(jsonPath("$.costing.marginPct").value(38.19));

        mvc.perform(get("/api/production-runs").param("from", "2026-11-01").param("to", "2026-11-30"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].runNo").value("RUN/2026-27/0001"))
                .andExpect(jsonPath("$[0].status").value("POSTED"));
        mvc.perform(get("/api/production-runs").param("from", "2026-12-01"))
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void cancelAndWrongStateAnswers() throws Exception {
        long id = createRun();
        mvc.perform(post("/api/production-runs/" + id + "/cancel"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
        mvc.perform(post("/api/production-runs/" + id + "/post"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(containsString("CANCELLED")));
    }

    @Test
    void postingTwiceIs409() throws Exception {
        long id = createRun();
        enterBenchmarkActuals(id);
        mvc.perform(post("/api/production-runs/" + id + "/post")).andExpect(status().isOk());

        mvc.perform(post("/api/production-runs/" + id + "/post"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(containsString("POSTED")));
    }

    @Test
    void insufficientStockIs409WithOneDetailPerShortItem() throws Exception {
        long id = createRun();
        mvc.perform(put("/api/production-runs/" + id + "/actuals").contentType(MediaType.APPLICATION_JSON).content("""
                {"actualOutput": 188, "lines": [
                  {"itemCode": "FLOUR",  "actualQty": 150, "uomCode": "kg"},
                  {"itemCode": "OIL",    "actualQty": 40,  "uomCode": "L"},
                  {"itemCode": "SPICE",  "actualQty": 1.2, "uomCode": "kg"},
                  {"itemCode": "POUCH",  "actualQty": 188, "uomCode": "pc"},
                  {"itemCode": "CARTON", "actualQty": 9,   "uomCode": "pc"}]}
                """)).andExpect(status().isOk());

        mvc.perform(post("/api/production-runs/" + id + "/post"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.details", hasSize(2)))
                .andExpect(jsonPath("$.details[0]").value(containsString("FLOUR")))
                .andExpect(jsonPath("$.details[1]").value(containsString("OIL")));
        mvc.perform(get("/api/production-runs/" + id)).andExpect(jsonPath("$.status").value("DRAFT"));
    }

    @Test
    void validationAndUnknownIds() throws Exception {
        mvc.perform(post("/api/production-runs").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productCode\": \"\", \"batches\": -1}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details", hasSize(3)));   // blank product, no date, negative batches
        mvc.perform(post("/api/production-runs").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productCode\": \"FLOUR\", \"runDate\": \"2026-11-03\", \"batches\": 1}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("finished good")));

        long id = createRun();
        mvc.perform(put("/api/production-runs/" + id + "/actuals").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"actualOutput\": 188, \"lines\": [{\"itemCode\": \"OIL\", \"actualQty\": 1, \"uomCode\": \"kg\"}]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("cannot be converted")));
        mvc.perform(post("/api/production-runs/" + id + "/overheads").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\": \"RENT\", \"amount\": 5}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/production-runs/" + id + "/overheads").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\": \"OTHER\", \"amount\": 0}"))
                .andExpect(status().isBadRequest());

        mvc.perform(get("/api/production-runs/999999")).andExpect(status().isNotFound());
        mvc.perform(post("/api/production-runs/999999/post")).andExpect(status().isNotFound());
        mvc.perform(get("/api/production-runs/abc")).andExpect(status().isBadRequest());
    }
}
