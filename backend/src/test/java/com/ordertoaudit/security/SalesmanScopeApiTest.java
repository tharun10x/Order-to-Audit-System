package com.ordertoaudit.security;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.jayway.jsonpath.JsonPath;
import com.ordertoaudit.AbstractSalesTest;
import com.ordertoaudit.ApiTokens;

/** R2.2 through the API, with real logins: a salesman sees only their own orders and customers. */
@AutoConfigureMockMvc
class SalesmanScopeApiTest extends AbstractSalesTest {

    @Autowired
    MockMvc mvc;

    private ResultActions placeOrder(String username, String customerCode) throws Exception {
        String body = """
                {"customerId": %d, "clientUuid": "%s",
                 "lines": [{"itemCode": "MURUKKU200", "qty": 10, "uomCode": "pkt"}]}
                """.formatted(customerId(customerCode), UUID.randomUUID());
        return mvc.perform(post("/api/orders").with(ApiTokens.as(mvc, username))
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private int idOf(ResultActions result) throws Exception {
        return JsonPath.read(result.andReturn().getResponse().getContentAsString(), "$.id");
    }

    private ResultActions getAs(String username, String url) throws Exception {
        return mvc.perform(get(url).with(ApiTokens.as(mvc, username)));
    }

    @Test
    void eachSalesmanListsOnlyTheirOwnOrders() throws Exception {
        int ofA = idOf(placeOrder("salesman_a", "RET-A")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.salesman").value("salesman_a")));
        int ofB = idOf(placeOrder("salesman_b", "DIST-B").andExpect(status().isCreated()));

        getAs("salesman_a", "/api/orders")
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[*].id", contains(ofA)));
        getAs("salesman_b", "/api/orders").andExpect(jsonPath("$[*].id", contains(ofB)));
        getAs("admin", "/api/orders").andExpect(jsonPath("$[*].id", containsInAnyOrder(ofA, ofB)));

        getAs("salesman_a", "/api/orders/" + ofA).andExpect(status().isOk());
        getAs("salesman_a", "/api/orders/" + ofB).andExpect(status().isNotFound());
        getAs("auditor", "/api/orders/" + ofB).andExpect(status().isOk());
    }

    @Test
    void aSalesmanCannotOrderForSomeoneElsesCustomer() throws Exception {
        placeOrder("salesman_a", "DIST-B").andExpect(status().isForbidden());
    }

    @Test
    void eachSalesmanListsOnlyTheirOwnCustomers() throws Exception {
        // intraState: the customer is in the factory's state (Tamil Nadu), so CGST + SGST apply (R10.3)
        getAs("salesman_a", "/api/customers")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].code", contains("RET-A")))
                .andExpect(jsonPath("$[0].intraState").value(true));
        getAs("salesman_b", "/api/customers")
                .andExpect(jsonPath("$[*].code", contains("DIST-B")))
                .andExpect(jsonPath("$[0].intraState").value(false));
        getAs("admin", "/api/customers").andExpect(jsonPath("$[*].code", contains("DIST-B", "RET-A")));
    }

    @Test
    void theProductListHasWhatOrderTakingNeeds() throws Exception {
        getAs("salesman_a", "/api/products")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))   // finished goods only
                .andExpect(jsonPath("$[0].code").value("MURUKKU200"))
                .andExpect(jsonPath("$[0].sellingPrice").value(50.0))
                .andExpect(jsonPath("$[0].hsnCode").value("21069099"))
                .andExpect(jsonPath("$[0].gstRate").value(5.0))
                .andExpect(jsonPath("$[0].packetsPerCarton").value(20.0));
    }
}
