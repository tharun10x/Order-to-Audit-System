package com.ordertoaudit.sales;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.jayway.jsonpath.JsonPath;
import com.ordertoaudit.AbstractSalesTest;
import com.ordertoaudit.ApiTokens;

/** The sales, billing and payment endpoints and their status codes. Requests are made as admin. */
@AutoConfigureMockMvc
class SalesApiTest extends AbstractSalesTest {

    @Autowired
    MockMvc mvc;

    @BeforeEach
    void stock() {
        produceBenchmarkStock();
    }

    /** Every request carries a real token of the seeded admin user. */
    private ResultActions perform(MockHttpServletRequestBuilder request) throws Exception {
        return mvc.perform(request.with(ApiTokens.as(mvc, "admin")));
    }

    private ResultActions postJson(String url, String body) throws Exception {
        return perform(post(url).contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private String orderBody(String customerCode, UUID uuid, int packets) {
        return """
                {"customerId": %d, "clientUuid": "%s",
                 "lines": [{"itemCode": "MURUKKU200", "qty": %d, "uomCode": "pkt"}]}
                """.formatted(customerId(customerCode), uuid, packets);
    }

    private long idOf(ResultActions result) throws Exception {
        String body = result.andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.id")).longValue();
    }

    @Test
    void orderToPaymentThroughTheEndpoints() throws Exception {
        UUID uuid = UUID.randomUUID();
        long orderId = idOf(postJson("/api/orders", orderBody("RET-A", uuid, 150))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("SUBMITTED")));
        // R9.7: the same request again is 200 with the same order
        postJson("/api/orders", orderBody("RET-A", uuid, 150))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value((int) orderId));
        perform(get("/api/orders").param("status", "SUBMITTED")).andExpect(jsonPath("$", hasSize(1)));
        perform(get("/api/orders/" + orderId + "/stock-check"))
                .andExpect(jsonPath("$.canApprove").value(true))
                .andExpect(jsonPath("$.lines[0].available").value(238.0));
        perform(put("/api/orders/" + orderId + "/lines").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"lines\": [{\"itemCode\": \"MURUKKU200\", \"qty\": 150, \"rate\": 50}]}"))
                .andExpect(status().isOk());
        perform(post("/api/orders/" + orderId + "/approve"))   // no body: approve in full
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"));

        long invoiceId = idOf(postJson("/api/invoices",
                "{\"orderId\": %d, \"invoiceDate\": \"2026-11-05\"}".formatted(orderId))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.invoiceNo").value("INV/2026-27/0001"))
                .andExpect(jsonPath("$.total").value(7875.0))
                .andExpect(jsonPath("$.buyer.name").value("Retailer A")));

        byte[] pdf = perform(get("/api/invoices/" + invoiceId + "/pdf"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_PDF))
                .andReturn().getResponse().getContentAsByteArray();
        assertThat(pdfText(pdf)).contains("INV/2026-27/0001", "7,875.00", "21069099");

        postJson("/api/dispatches",
                "{\"invoiceId\": %d, \"dispatchDate\": \"2026-11-06\", \"vehicleNo\": \"TN45\"}".formatted(invoiceId))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.dispatchNo").value("DC/2026-27/0001"));
        postJson("/api/payments", """
                {"customerId": %d, "receivedOn": "2026-11-10", "amount": 4000, "mode": "UPI",
                 "allocations": [{"invoiceId": %d, "amount": 4000}]}
                """.formatted(customerId("RET-A"), invoiceId))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.allocations[0].outstanding").value(3875.0));

        perform(get("/api/customers/" + customerId("RET-A") + "/outstanding"))
                .andExpect(jsonPath("$.totalOutstanding").value(3875.0));
        perform(get("/api/payments"))
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].amount").value(4000.0))
                .andExpect(jsonPath("$[0].allocations[0].invoiceNo").value("INV/2026-27/0001"))
                .andExpect(jsonPath("$[0].allocations[0].paymentStatus").value("PARTIALLY_PAID"));
        perform(get("/api/invoices")).andExpect(jsonPath("$", hasSize(1)));
        perform(get("/api/invoices/" + invoiceId))
                .andExpect(jsonPath("$.paymentStatus").value("PARTIALLY_PAID"));
        postJson("/api/invoices/" + invoiceId + "/cancel", "{\"reason\": \"x\", \"noteDate\": \"2026-11-12\"}")
                .andExpect(status().isConflict());
    }

    @Test
    void aShortageIs409WithADetailLine() throws Exception {
        long orderId = idOf(postJson("/api/orders", orderBody("DIST-B", UUID.randomUUID(), 300)));

        perform(post("/api/orders/" + orderId + "/approve"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.details[0]")
                        .value("MURUKKU200: ordered 300, available 238, short by 62, batches needed 1"));
        postJson("/api/orders/" + orderId + "/approve",
                "{\"lines\": [{\"itemCode\": \"MURUKKU200\", \"approvedQty\": 238}]}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lines[0].approvedQty").value(238.0));
        perform(post("/api/orders/" + orderId + "/cancel"))
                .andExpect(jsonPath("$.status").value("CANCELLED"));
    }

    @Test
    void cancelAndRejectThroughTheEndpoints() throws Exception {
        long rejected = idOf(postJson("/api/orders", orderBody("RET-A", UUID.randomUUID(), 1)));
        postJson("/api/orders/" + rejected + "/reject", "{\"reason\": \" \"}").andExpect(status().isBadRequest());
        postJson("/api/orders/" + rejected + "/reject", "{\"reason\": \"No\"}")
                .andExpect(jsonPath("$.status").value("REJECTED"));

        long invoiceId = retailerInvoice().id();
        postJson("/api/invoices/" + invoiceId + "/cancel", "{\"reason\": \"Wrong\", \"noteDate\": \"2026-11-12\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"))
                .andExpect(jsonPath("$.creditNoteNo").value("CN/2026-27/0001"));
    }

    @Test
    void badRequestsAndUnknownIds() throws Exception {
        postJson("/api/orders", "{\"lines\": []}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details", hasSize(3)));   // no customer, no clientUuid, no lines
        postJson("/api/orders", orderBody("RET-A", UUID.randomUUID(), 1).replace("MURUKKU200", "FLOUR"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("finished good")));
        postJson("/api/payments", """
                {"customerId": %d, "receivedOn": "2026-11-10", "amount": 10, "mode": "GOLD", "allocations": []}
                """.formatted(customerId("RET-A")))
                .andExpect(status().isBadRequest());

        perform(get("/api/orders/999999")).andExpect(status().isNotFound());
        perform(get("/api/invoices/999999/pdf")).andExpect(status().isNotFound());
        perform(get("/api/customers/999999/outstanding")).andExpect(status().isNotFound());
        postJson("/api/dispatches", "{\"invoiceId\": 999999, \"dispatchDate\": \"2026-11-06\"}")
                .andExpect(status().isNotFound());
        postJson("/api/invoices", "{\"orderId\": 999999, \"invoiceDate\": \"2026-11-05\"}")
                .andExpect(status().isNotFound());
    }
}
