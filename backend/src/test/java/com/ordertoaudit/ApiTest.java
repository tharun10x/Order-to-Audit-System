package com.ordertoaudit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.ordertoaudit.common.error.ErrorResponse;
import com.ordertoaudit.common.error.GlobalExceptionHandler;
import com.ordertoaudit.common.error.InsufficientStockException;

/** The three endpoints and the 400 / 409 error bodies. */
@AutoConfigureMockMvc
class ApiTest extends AbstractLedgerTest {

    @Autowired
    MockMvc mvc;

    private ResultActions postPurchase(String billNo, String itemCode, String qty, String uom, String rate) throws Exception {
        String body = """
                {"supplierId": %d, "purchaseDate": "2026-11-02", "billNo": "%s",
                 "lines": [{"itemCode": "%s", "qty": %s, "uomCode": "%s", "rate": %s}]}
                """.formatted(supplierId(), billNo, itemCode, qty, uom, rate);
        return mvc.perform(post("/api/purchases").contentType(MediaType.APPLICATION_JSON).content(body));
    }

    @Test
    void postingAPurchaseReturns201AndTheBalancesAndLedgerShowIt() throws Exception {
        postPurchase("API-1", "FLOUR", "60", "kg", "45")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.billNo").value("API-1"))
                .andExpect(jsonPath("$.status").value("POSTED"))
                .andExpect(jsonPath("$.total").value(2700.0))
                .andExpect(jsonPath("$.lines[0].itemCode").value("FLOUR"))
                .andExpect(jsonPath("$.lines[0].qty").value(60.0));

        mvc.perform(get("/api/inventory/balances"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(6)))
                .andExpect(jsonPath("$[?(@.itemCode=='FLOUR')].qtyOnHand", contains(160.0)))
                .andExpect(jsonPath("$[?(@.itemCode=='FLOUR')].stockValue", contains(6900.0)))
                .andExpect(jsonPath("$[?(@.itemCode=='FLOUR')].avgCost", contains(43.125)));

        // Whole ledger: the opening row, then the receipt with the running balance
        mvc.perform(get("/api/inventory/ledger").param("itemCode", "FLOUR"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].movementType").value("OPENING"))
                .andExpect(jsonPath("$[1].movementType").value("RECEIPT"))
                .andExpect(jsonPath("$[1].sourceType").value("PURCHASE"))
                .andExpect(jsonPath("$[1].runningQty").value(160.0))
                .andExpect(jsonPath("$[1].runningValue").value(6900.0));

        // Date filter: only the 2 November movement, but the running balance still includes the opening stock
        mvc.perform(get("/api/inventory/ledger").param("itemCode", "FLOUR").param("from", "2026-11-02").param("to", "2026-11-02"))
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].runningQty").value(160.0));
        mvc.perform(get("/api/inventory/ledger").param("itemCode", "FLOUR").param("from", "2026-11-03"))
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void validationErrorsReturn400WithDetails() throws Exception {
        String body = """
                {"supplierId": %d, "purchaseDate": "2026-11-02", "billNo": " ",
                 "lines": [{"itemCode": "FLOUR", "qty": -5, "uomCode": "kg", "rate": 45}]}
                """.formatted(supplierId());
        mvc.perform(post("/api/purchases").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("Validation failed"))
                .andExpect(jsonPath("$.details", hasSize(2)));

        mvc.perform(post("/api/purchases").contentType(MediaType.APPLICATION_JSON).content("{ not json"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void wrongUnitReturns400() throws Exception {
        postPurchase("API-2", "FLOUR", "60", "L", "45")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("cannot be converted")));
        postPurchase("API-3", "POUCH", "100", "pkt", "2.5")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("POUCH")));
    }

    @Test
    void duplicateBillReturns409() throws Exception {
        postPurchase("API-4", "FLOUR", "1", "kg", "45").andExpect(status().isCreated());
        postPurchase("API-4", "OIL", "1", "L", "140")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.message").value(containsString("API-4")));
    }

    @Test
    void repeatedItemOnOneBillReturns400() throws Exception {
        String body = """
                {"supplierId": %d, "purchaseDate": "2026-11-02", "billNo": "API-5",
                 "lines": [{"itemCode": "FLOUR", "qty": 1, "uomCode": "kg", "rate": 45},
                           {"itemCode": "FLOUR", "qty": 2, "uomCode": "kg", "rate": 45}]}
                """.formatted(supplierId());
        mvc.perform(post("/api/purchases").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("more than one line")));
    }

    @Test
    void unknownReferencesReturn400() throws Exception {
        postPurchase("API-6", "NOPE", "1", "kg", "45").andExpect(status().isBadRequest());
        postPurchase("API-7", "MURUKKU200", "1", "pkt", "45")   // finished goods are not purchased
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/inventory/ledger").param("itemCode", "NOPE")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/inventory/ledger")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/inventory/ledger").param("itemCode", "FLOUR").param("from", "not-a-date"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/inventory/ledger").param("itemCode", "FLOUR").param("from", "2026-11-05").param("to", "2026-11-01"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void insufficientStockMapsTo409NamingTheItemAndShortage() {
        // No endpoint issues stock yet (production comes in step 4), so the handler is checked directly
        var exception = new InsufficientStockException("FLOUR", new BigDecimal("100.0000"), new BigDecimal("150.0000"));

        ResponseEntity<ErrorResponse> response = new GlobalExceptionHandler().conflict(exception);

        assertThat(response.getStatusCode().value()).isEqualTo(409);
        assertThat(response.getBody().message()).contains("FLOUR", "short by 50.0000");
    }
}
