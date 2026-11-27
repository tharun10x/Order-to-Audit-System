package com.ordertoaudit;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.ordertoaudit.billing.web.InvoiceResponse;
import com.ordertoaudit.production.entity.OverheadType;
import com.ordertoaudit.production.web.OverheadRequest;

/**
 * The fields and endpoints added for frontend milestone 2 (H1 to H9), through the API with real logins.
 * State: the benchmark run posted with its overheads, Retailer A invoiced, dispatched and paid 4,000.00.
 */
@AutoConfigureMockMvc
class FrontendApiTest extends AbstractSalesTest {

    @Autowired
    MockMvc mvc;

    private InvoiceResponse invoice;
    private Long runId;

    @BeforeEach
    void benchmark() {
        produceBenchmarkStock();
        runId = jdbc.queryForObject("SELECT id FROM production_run", Long.class);
        productionService.addOverhead(runId, new OverheadRequest(OverheadType.LABOUR, new BigDecimal("1200"), null), admin());
        productionService.addOverhead(runId, new OverheadRequest(OverheadType.POWER_GAS, new BigDecimal("600"), null), admin());
        invoice = retailerInvoice();
        dispatch(invoice.id());
        pay("RET-A", invoice.id(), "4000.00");
    }

    private ResultActions getAs(String username, String url) throws Exception {
        return mvc.perform(get(url).with(ApiTokens.as(mvc, username)));
    }

    @Test
    void suppliersAndItemsForThePurchaseAndRunForms() throws Exception {
        getAs("production", "/api/suppliers")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].name", contains("Sample Flour Mills")));
        getAs("auditor", "/api/items")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(6)))
                .andExpect(jsonPath("$[?(@.code=='OIL')].baseUnit", contains("L")))
                .andExpect(jsonPath("$[?(@.code=='MURUKKU200')].hasActiveBom", contains(true)))
                .andExpect(jsonPath("$[?(@.code=='FLOUR')].hasActiveBom", contains(false)));
    }

    @Test
    void auditorAndOwnerCanListAllCustomers() throws Exception {
        getAs("auditor", "/api/customers").andExpect(jsonPath("$[*].code", contains("DIST-B", "RET-A")));
        getAs("owner", "/api/customers").andExpect(status().isOk());
    }

    @Test
    void quantitiesCarryTheirUnit() throws Exception {
        getAs("production", "/api/inventory/balances")
                .andExpect(jsonPath("$[?(@.itemCode=='FLOUR')].unit", contains("kg")))
                .andExpect(jsonPath("$[?(@.itemCode=='MURUKKU200')].unit", contains("pkt")));
        getAs("production", "/api/production-runs/" + runId)
                .andExpect(jsonPath("$.lines[?(@.itemCode=='OIL')].unit", contains("L")))
                .andExpect(jsonPath("$.lines[?(@.itemCode=='POUCH')].unit", contains("pc")));
        getAs("production", "/api/purchases")
                .andExpect(jsonPath("$[0].lines[0].itemName").value("Flour"))
                .andExpect(jsonPath("$[0].lines[0].unit").value("kg"));
    }

    @Test
    void theRunListHasYieldAndCostPerPacket() throws Exception {
        getAs("admin", "/api/production-runs")
                .andExpect(jsonPath("$[0].runNo").value("RUN/2026-27/0001"))
                .andExpect(jsonPath("$[0].yieldVariancePct").value(-6.0))
                .andExpect(jsonPath("$[0].yieldFlagged").value(true))
                .andExpect(jsonPath("$[0].costPerPacket").value(31.14));
    }

    @Test
    void ledgerRowsNameTheirDocument() throws Exception {
        getAs("auditor", "/api/inventory/ledger?itemCode=MURUKKU200")
                .andExpect(jsonPath("$[*].documentNo",
                        contains("Opening stock", "RUN/2026-27/0001", "DC/2026-27/0001 / INV/2026-27/0001")))
                .andExpect(jsonPath("$[2].invoiceId").value(invoice.id().intValue()));
    }

    @Test
    void invoicesCanBeFilteredAndShowTaxDispatchAndPayments() throws Exception {
        getAs("admin", "/api/invoices/" + invoice.id())
                .andExpect(jsonPath("$.taxTotal").value(375.0))
                .andExpect(jsonPath("$.dispatchNo").value("DC/2026-27/0001"))
                .andExpect(jsonPath("$.dispatchDate").value("2026-11-06"))
                .andExpect(jsonPath("$.payments", hasSize(1)))
                .andExpect(jsonPath("$.payments[0].amount").value(4000.0))
                .andExpect(jsonPath("$.payments[0].mode").value("UPI"))
                .andExpect(jsonPath("$.payments[0].receivedOn").value("2026-11-10"))
                .andExpect(jsonPath("$.outstanding").value(3875.0));

        getAs("admin", "/api/invoices?status=POSTED&from=2026-11-01&to=2026-11-30").andExpect(jsonPath("$", hasSize(1)));
        getAs("admin", "/api/invoices?status=CANCELLED").andExpect(jsonPath("$", hasSize(0)));
        getAs("admin", "/api/invoices?from=2026-11-06").andExpect(jsonPath("$", hasSize(0)));   // invoiced on the 5th
        getAs("admin", "/api/invoices?to=2026-11-05").andExpect(jsonPath("$", hasSize(1)));
        getAs("admin", "/api/invoices?from=2026-11-30&to=2026-11-01").andExpect(status().isBadRequest());
        getAs("admin", "/api/invoices?status=PAID").andExpect(status().isBadRequest());
    }

    @Test
    void paymentsNameTheCustomerAndOutstandingHasAgeing() throws Exception {
        getAs("owner", "/api/payments").andExpect(jsonPath("$[0].customerName").value("Retailer A"));
        getAs("auditor", "/api/customers/" + customerId("RET-A") + "/outstanding")
                .andExpect(jsonPath("$.asOf").value("2026-11-28"))
                .andExpect(jsonPath("$.totalOutstanding").value(3875.0))
                .andExpect(jsonPath("$.buckets['0-30']").value(3875.0))
                .andExpect(jsonPath("$.buckets['90+']").value(0.0))
                .andExpect(jsonPath("$.invoices[0].days").value(23))
                .andExpect(jsonPath("$.invoices[0].ageingBucket").value("0-30"));
        getAs("auditor", "/api/customers/" + customerId("DIST-B") + "/outstanding")
                .andExpect(jsonPath("$.totalOutstanding").value(0.0))
                .andExpect(jsonPath("$.invoices", hasSize(0)));
    }
}
