package com.ordertoaudit.masters;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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

/** Customer, supplier and item masters (R5.1 to R5.4, M1 to M10), each change audited (R14). */
@AutoConfigureMockMvc
class MastersApiTest extends AbstractLedgerTest {

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

    private long userId(String username) {
        return jdbc.queryForObject("SELECT id FROM app_user WHERE username = ?", Long.class, username);
    }

    private long customerId(String code) {
        return jdbc.queryForObject("SELECT id FROM customer WHERE code = ?", Long.class, code);
    }

    private String customer(String code, String stateCode, String gstin, String salesman) {
        return """
                {"code": "%s", "name": "Karnataka Traders", "type": "DISTRIBUTOR", "stateCode": "%s",
                 "gstin": %s, "address": "Mysuru", "phone": "9800000000", "salesmanId": %d}
                """.formatted(code, stateCode, gstin == null ? "null" : "\"" + gstin + "\"", userId(salesman));
    }

    /** The newest audit row for an entity: action, before and after (JSON text). */
    private Map<String, Object> lastAudit(String entityType) {
        return jdbc.queryForMap("SELECT action, before_data::text AS before, after_data::text AS after, username "
                + "FROM audit_log WHERE entity_type = ? ORDER BY id DESC LIMIT 1", entityType);
    }

    // ------------------------------------------------------------------ customers

    @Test
    void aCustomerInKarnatakaIsInterStateAndTheCreationIsAudited() throws Exception {
        admin("POST", "/api/customers", customer("kt-1", "29", "29aaack1234b1z2", "salesman_b"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value("KT-1"))                     // codes are upper-cased
                .andExpect(jsonPath("$.gstin").value("29AAACK1234B1Z2"))
                .andExpect(jsonPath("$.intraState").value(false))               // R10.3: IGST
                .andExpect(jsonPath("$.salesmanName").value("Salesman B"))
                .andExpect(jsonPath("$.active").value(true));

        // R2.2: the salesman it is assigned to sees it; the other one does not
        send("salesman_b", "GET", "/api/customers", null).andExpect(jsonPath("$[*].code", hasItem("KT-1")));
        send("salesman_a", "GET", "/api/customers", null).andExpect(jsonPath("$[*].code", not(hasItem("KT-1"))));

        Map<String, Object> audit = lastAudit("CUSTOMER");
        assertThat(audit.get("action")).isEqualTo("CREATE");
        assertThat(audit.get("before")).isNull();
        assertThat((String) audit.get("after")).contains("\"stateCode\": \"29\"", "\"salesman\": \"salesman_b\"");
        assertThat(audit.get("username")).isEqualTo("admin");
    }

    @Test
    void aGstinMustFitTheFormatAndStartWithTheStateCode() throws Exception {
        admin("POST", "/api/customers", customer("KT-2", "33", "29AAACK1234B1Z2", "salesman_a"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("starts with 29, but the state code is 33")));
        admin("POST", "/api/customers", customer("KT-2", "29", "29AAACK1234B1X2", "salesman_a"))   // no Z
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("is not in the GSTIN format")));
        admin("POST", "/api/customers", customer("KT-2", "29", "29AAACK1234B1Z", "salesman_a"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("has 14")));
        admin("POST", "/api/customers", customer("KT-2", "00", null, "salesman_a"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("is not a GST state code")));
        // Unregistered (B2C) is fine
        admin("POST", "/api/customers", customer("KT-2", "33", null, "salesman_a")).andExpect(status().isCreated());
    }

    @Test
    void theSalesmanMustBeAnActiveSalesmanAndTheCodeIsUniqueAndFixed() throws Exception {
        admin("POST", "/api/customers", customer("KT-3", "33", null, "production"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("active user with the Salesman role")));
        admin("POST", "/api/customers", customer("RET-A", "33", null, "salesman_a")).andExpect(status().isConflict());

        admin("GET", "/api/salesmen", null)
                .andExpect(jsonPath("$[*].username", hasItem("salesman_a")))
                .andExpect(jsonPath("$[*].username", not(hasItem("production"))));

        long id = customerId("RET-A");
        admin("PUT", "/api/customers/" + id, customer("RET-X", "33", null, "salesman_a"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("code cannot be changed")));
    }

    @Test
    void anEditIsAuditedWithTheValuesBeforeAndAfter() throws Exception {
        admin("POST", "/api/customers", customer("KT-4", "29", null, "salesman_b")).andExpect(status().isCreated());
        String edited = customer("KT-4", "29", null, "salesman_b").replace("9800000000", "9811111111");

        admin("PUT", "/api/customers/" + customerId("KT-4"), edited)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.phone").value("9811111111"));

        Map<String, Object> audit = lastAudit("CUSTOMER");
        assertThat(audit.get("action")).isEqualTo("UPDATE");
        assertThat((String) audit.get("before")).contains("\"phone\": \"9800000000\"");
        assertThat((String) audit.get("after")).contains("\"phone\": \"9811111111\"");
    }

    @Test
    void aDeactivatedCustomerCannotOrderAndIsListedOnlyAsInactive() throws Exception {
        long retailerA = customerId("RET-A");
        admin("POST", "/api/customers/" + retailerA + "/deactivate", null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false));
        Map<String, Object> audit = lastAudit("CUSTOMER");
        assertThat((String) audit.get("before")).contains("\"active\": true");
        assertThat((String) audit.get("after")).contains("\"active\": false");

        send("salesman_a", "POST", "/api/orders", """
                {"customerId": %d, "clientUuid": "%s", "lines": [{"itemCode": "MURUKKU200", "qty": 10, "uomCode": "pkt"}]}
                """.formatted(retailerA, UUID.randomUUID()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Customer Retailer A is inactive"));

        admin("GET", "/api/customers", null).andExpect(jsonPath("$[*].code", not(hasItem("RET-A"))));
        admin("GET", "/api/customers?status=inactive", null).andExpect(jsonPath("$[*].code", hasItem("RET-A")));
        admin("GET", "/api/customers?status=all", null).andExpect(jsonPath("$[*].code", hasItem("RET-A")));
        admin("GET", "/api/customers?status=gone", null).andExpect(status().isBadRequest());

        admin("POST", "/api/customers/" + retailerA + "/reactivate", null).andExpect(jsonPath("$.active").value(true));
    }

    // ------------------------------------------------------------------ suppliers

    @Test
    void suppliersAreCreatedEditedAndDeactivatedWithUniqueActiveNames() throws Exception {
        String body = "{\"name\": \"Chennai Oils\", \"stateCode\": \"33\", \"gstin\": \"33AAACC2222C1Z1\", \"phone\": \"044\"}";
        String created = admin("POST", "/api/suppliers", body)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.active").value(true))
                .andReturn().getResponse().getContentAsString();
        long id = ((Number) JsonPath.read(created, "$.id")).longValue();

        admin("POST", "/api/suppliers", body.replace("Chennai Oils", "chennai oils"))
                .andExpect(status().isConflict());
        admin("POST", "/api/suppliers", "{\"name\": \"No State\", \"gstin\": \"33AAACC2222C1Z1\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("Choose the state")));

        admin("PUT", "/api/suppliers/" + id, body.replace("044", "0431")).andExpect(jsonPath("$.phone").value("0431"));
        admin("POST", "/api/suppliers/" + id + "/deactivate", null).andExpect(jsonPath("$.active").value(false));
        send("production", "GET", "/api/suppliers", null).andExpect(jsonPath("$[*].name", not(hasItem("Chennai Oils"))));
        assertThat(lastAudit("SUPPLIER").get("after").toString()).contains("\"active\": false");

        // An inactive supplier cannot be used on a purchase (R5.4)
        send("production", "POST", "/api/purchases", """
                {"supplierId": %d, "purchaseDate": "2026-11-27", "billNo": "CO-1",
                 "lines": [{"itemCode": "OIL", "qty": 5, "uomCode": "L", "rate": 140}]}""".formatted(id))
                .andExpect(status().isBadRequest());
    }

    // ------------------------------------------------------------------ items

    private static final String MURUKKU200_AT_55 = """
            {"name": "Murukku 200 g", "kind": "FG", "baseUnit": "pkt", "hsnCode": "21069099", "gstRate": 5,
             "sellingPrice": 55, "packWeightKg": 0.2, "packetsPerCarton": 20}""";

    @Test
    void aPriceChangeAffectsOnlyNewOrders() throws Exception {
        String order = """
                {"customerId": %d, "clientUuid": "%s", "lines": [{"itemCode": "MURUKKU200", "qty": 10, "uomCode": "pkt"}]}""";
        String before = send("salesman_a", "POST", "/api/orders", order.formatted(customerId("RET-A"), UUID.randomUUID()))
                .andExpect(jsonPath("$.lines[0].rate").value(50.0))
                .andReturn().getResponse().getContentAsString();
        long oldOrder = ((Number) JsonPath.read(before, "$.id")).longValue();

        admin("PUT", "/api/items/MURUKKU200", MURUKKU200_AT_55)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sellingPrice").value(55.0))
                .andExpect(jsonPath("$.packetsPerCarton").value(20));
        Map<String, Object> audit = lastAudit("ITEM");
        assertThat((String) audit.get("before")).contains("\"sellingPrice\": 50.0");
        assertThat((String) audit.get("after")).contains("\"sellingPrice\": 55");

        // R9.2: the order copied the price when it was taken
        admin("GET", "/api/orders/" + oldOrder, null)
                .andExpect(jsonPath("$.lines[0].rate").value(50.0))
                .andExpect(jsonPath("$.taxableValue").value(500.0));
        send("salesman_a", "POST", "/api/orders", order.formatted(customerId("RET-A"), UUID.randomUUID()))
                .andExpect(jsonPath("$.lines[0].rate").value(55.0));
        send("salesman_a", "GET", "/api/products", null).andExpect(jsonPath("$[0].sellingPrice").value(55.0));
    }

    @Test
    void theBaseUnitIsLockedOnceTheItemHasMoved() throws Exception {
        admin("GET", "/api/items", null)
                .andExpect(jsonPath("$[?(@.code=='FLOUR')].baseUnitLockReason",
                        hasItem(containsString("FLOUR has stock movements"))));

        admin("PUT", "/api/items/FLOUR", "{\"name\": \"Flour\", \"baseUnit\": \"L\"}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(
                        "The base unit stays kg: FLOUR has stock movements, and the ledger quantities are in kg"));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM audit_log WHERE entity_type = 'ITEM'", Integer.class))
                .isZero();

        // A new item that has not moved can still change its unit, and the name alone can always change
        admin("POST", "/api/items", "{\"code\": \"GHEE\", \"name\": \"Ghee\", \"kind\": \"RAW\", \"baseUnit\": \"kg\"}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.baseUnitLockReason").doesNotExist())
                .andExpect(jsonPath("$.units[0]").value("kg"));
        admin("PUT", "/api/items/GHEE", "{\"name\": \"Ghee\", \"baseUnit\": \"L\", \"reorderLevel\": 5}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.baseUnit").value("L"))
                .andExpect(jsonPath("$.units[1]").value("ml"));
        admin("PUT", "/api/items/FLOUR", "{\"name\": \"Wheat flour\", \"baseUnit\": \"kg\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Wheat flour"));
        jdbc.update("UPDATE item SET name = 'Flour' WHERE code = 'FLOUR'");
    }

    @Test
    void itemRulesForCodeKindUnitsAndFinishedGoodsFields() throws Exception {
        admin("POST", "/api/items", "{\"code\": \"FLOUR\", \"name\": \"X\", \"kind\": \"RAW\", \"baseUnit\": \"kg\"}")
                .andExpect(status().isConflict());
        admin("POST", "/api/items", "{\"code\": \"SUGAR\", \"name\": \"Sugar\", \"kind\": \"RAW\", \"baseUnit\": \"g\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("must be kg, L, pc or pkt")));
        admin("POST", "/api/items", "{\"code\": \"SUGAR\", \"name\": \"Sugar\", \"kind\": \"RAW\", \"baseUnit\": \"kg\", "
                        + "\"sellingPrice\": 10}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("Only finished goods")));
        admin("POST", "/api/items", "{\"code\": \"MIX500\", \"name\": \"Mix\", \"kind\": \"FG\", \"baseUnit\": \"kg\", "
                        + "\"hsnCode\": \"2106\", \"gstRate\": 5, \"sellingPrice\": 90, \"packWeightKg\": 0.5}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("base unit is pkt")));
        admin("POST", "/api/items", "{\"code\": \"MIX500\", \"name\": \"Mix\", \"kind\": \"FG\", \"baseUnit\": \"pkt\", "
                        + "\"hsnCode\": \"21069\", \"gstRate\": 5, \"sellingPrice\": 90, \"packWeightKg\": 0.5}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("4, 6 or 8 digits")));
        admin("POST", "/api/items", "{\"code\": \"MIX500\", \"name\": \"Mix\", \"kind\": \"FG\", \"baseUnit\": \"pkt\", "
                        + "\"hsnCode\": \"2106\", \"gstRate\": 5, \"packWeightKg\": 0.5}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("A finished good needs its selling price"));

        // Created with cartons of 12, then the carton removed; code and kind cannot change
        admin("POST", "/api/items", "{\"code\": \"MIX500\", \"name\": \"Mix\", \"kind\": \"FG\", \"baseUnit\": \"pkt\", "
                        + "\"hsnCode\": \"2106\", \"gstRate\": 5, \"sellingPrice\": 90, \"packWeightKg\": 0.5, "
                        + "\"packetsPerCarton\": 12}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.units[1]").value("ctn"))
                .andExpect(jsonPath("$.hasActiveBom").value(false));
        String noCarton = "{\"name\": \"Mix\", \"baseUnit\": \"pkt\", \"hsnCode\": \"2106\", \"gstRate\": 5, "
                + "\"sellingPrice\": 90, \"packWeightKg\": 0.5}";
        admin("PUT", "/api/items/MIX500", noCarton)
                .andExpect(jsonPath("$.packetsPerCarton").doesNotExist())
                .andExpect(jsonPath("$.units.length()").value(1));
        admin("PUT", "/api/items/MIX500", noCarton.replace("{", "{\"code\": \"MIX501\", "))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("code cannot be changed")));
        admin("PUT", "/api/items/MIX500", noCarton.replace("{", "{\"kind\": \"RAW\", "))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("kind cannot be changed")));
    }

    @Test
    void anItemInAnActiveRecipeCannotBeDeactivated() throws Exception {
        admin("POST", "/api/items/FLOUR/deactivate", null)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(
                        "FLOUR is in the active recipe of MURUKKU200. Activate a recipe version without it first"));

        // A finished good can be deactivated: no new runs for it
        admin("POST", "/api/items/MURUKKU200/deactivate", null).andExpect(jsonPath("$.active").value(false));
        send("production", "GET", "/api/items", null).andExpect(jsonPath("$[*].code", not(hasItem("MURUKKU200"))));
        send("production", "POST", "/api/production-runs",
                "{\"productCode\": \"MURUKKU200\", \"runDate\": \"2026-11-28\", \"batches\": 1}")
                .andExpect(status().isBadRequest());
    }
}
