package com.ordertoaudit.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import com.ordertoaudit.AbstractLedgerTest;
import com.ordertoaudit.ApiTokens;
import com.ordertoaudit.masters.entity.UserRole;

/**
 * Every endpoint against every role (R2). The table here is written out on its own, not built from the Roles
 * constants, so a wrong constant or annotation makes this test fail.
 *
 * Requests are well-formed but point at records that do not exist: Spring validates the body before it checks
 * the role, so a malformed one would answer 400 to everybody. An allowed role therefore gets 200, 400 or 404
 * from the application itself, and nothing is changed.
 */
@AutoConfigureMockMvc
class RoleMatrixTest extends AbstractLedgerTest {

    private static final Set<UserRole> EVERYONE = Set.of(UserRole.values());
    private static final Set<UserRole> ADMIN = Set.of(UserRole.ADMIN);
    private static final Set<UserRole> PRODUCTION = Set.of(UserRole.PRODUCTION);
    private static final Set<UserRole> ORDER_ENTRY = Set.of(UserRole.SALESMAN, UserRole.ADMIN);
    private static final Set<UserRole> ORDER_READ =
            Set.of(UserRole.SALESMAN, UserRole.ADMIN, UserRole.AUDITOR, UserRole.OWNER);
    private static final Set<UserRole> ACCOUNTS_READ = Set.of(UserRole.ADMIN, UserRole.AUDITOR, UserRole.OWNER);
    private static final Set<UserRole> PURCHASE_ENTRY = Set.of(UserRole.ADMIN, UserRole.PRODUCTION);
    private static final Set<UserRole> OPERATIONS_READ =
            Set.of(UserRole.ADMIN, UserRole.PRODUCTION, UserRole.AUDITOR, UserRole.OWNER);

    private static final Set<UserRole> REPORTS = Set.of(UserRole.ADMIN, UserRole.AUDITOR, UserRole.OWNER);
    private static final Set<UserRole> SALESMAN = Set.of(UserRole.SALESMAN);
    private static final Set<UserRole> DASHBOARD = Set.of(UserRole.OWNER, UserRole.ADMIN);

    private static final Map<UserRole, String> USERNAMES = Map.of(
            UserRole.SALESMAN, "salesman_a", UserRole.ADMIN, "admin", UserRole.PRODUCTION, "production",
            UserRole.AUDITOR, "auditor", UserRole.OWNER, "owner");

    /** pattern is the mapping as the controller declares it; url and body are what the test sends. */
    record Endpoint(String method, String pattern, String url, String body, int statusWhenAllowed,
                    Set<UserRole> allowed) {

        @Override
        public String toString() {
            return method + " " + pattern;
        }
    }

    private static Endpoint get(String pattern, int statusWhenAllowed, Set<UserRole> allowed) {
        return new Endpoint("GET", pattern, pattern.replace("{id}", "999999"), null, statusWhenAllowed, allowed);
    }

    private static Endpoint send(String method, String pattern, String body, int statusWhenAllowed,
                                 Set<UserRole> allowed) {
        return new Endpoint(method, pattern, pattern.replace("{id}", "999999"), body, statusWhenAllowed, allowed);
    }

    static List<Endpoint> endpoints() {
        return List.of(
                get("/api/auth/me", 200, EVERYONE),
                get("/api/customers", 200, ORDER_READ),   // H9: auditor and owner too, read-only
                get("/api/suppliers", 200, PURCHASE_ENTRY),
                get("/api/items", 200, OPERATIONS_READ),
                get("/api/products", 200, ORDER_ENTRY),

                send("POST", "/api/orders", """
                        {"customerId": 999999, "clientUuid": "00000000-0000-0000-0000-000000000001",
                         "lines": [{"itemCode": "MURUKKU200", "qty": 1, "uomCode": "pkt"}]}""", 400, ORDER_ENTRY),
                get("/api/orders", 200, ORDER_READ),
                get("/api/orders/{id}", 404, ORDER_READ),
                get("/api/orders/{id}/stock-check", 404, ADMIN),
                send("PUT", "/api/orders/{id}/lines",
                        "{\"lines\": [{\"itemCode\": \"MURUKKU200\", \"qty\": 1, \"rate\": 50}]}", 404, ADMIN),
                send("POST", "/api/orders/{id}/approve", null, 404, ADMIN),
                send("POST", "/api/orders/{id}/reject", "{\"reason\": \"No\"}", 404, ADMIN),
                send("POST", "/api/orders/{id}/cancel", null, 404, ADMIN),

                send("POST", "/api/invoices", "{\"orderId\": 999999, \"invoiceDate\": \"2026-11-05\"}", 404, ADMIN),
                get("/api/invoices", 200, ACCOUNTS_READ),
                get("/api/invoices/{id}", 404, ACCOUNTS_READ),
                get("/api/invoices/{id}/pdf", 404, ACCOUNTS_READ),
                send("POST", "/api/invoices/{id}/cancel", "{\"reason\": \"No\"}", 404, ADMIN),

                send("POST", "/api/dispatches", "{\"invoiceId\": 999999, \"dispatchDate\": \"2026-11-06\"}", 404, ADMIN),

                send("POST", "/api/payments", """
                        {"customerId": 999999, "receivedOn": "2026-11-10", "amount": 1, "mode": "CASH",
                         "allocations": [{"invoiceId": 999999, "amount": 1}]}""", 400, ADMIN),
                get("/api/payments", 200, ACCOUNTS_READ),
                get("/api/customers/{id}/outstanding", 404, ACCOUNTS_READ),

                send("POST", "/api/purchases", """
                        {"supplierId": 999999, "purchaseDate": "2026-11-02", "billNo": "M-1",
                         "lines": [{"itemCode": "FLOUR", "qty": 1, "uomCode": "kg", "rate": 45}]}""",
                        400, PURCHASE_ENTRY),
                get("/api/purchases", 200, OPERATIONS_READ),

                send("POST", "/api/production-runs",
                        "{\"productCode\": \"NOPE\", \"runDate\": \"2026-11-03\", \"batches\": 1}", 400, PRODUCTION),
                send("PUT", "/api/production-runs/{id}/actuals", """
                        {"actualOutput": 1, "lines": [{"itemCode": "FLOUR", "actualQty": 1, "uomCode": "kg"}]}""",
                        404, PRODUCTION),
                send("POST", "/api/production-runs/{id}/post", null, 404, PRODUCTION),
                send("POST", "/api/production-runs/{id}/cancel", null, 404, PRODUCTION),
                send("POST", "/api/production-runs/{id}/overheads", "{\"type\": \"OTHER\", \"amount\": 1}",
                        404, PRODUCTION),
                get("/api/production-runs", 200, OPERATIONS_READ),
                get("/api/production-runs/{id}", 404, OPERATIONS_READ),

                get("/api/inventory/balances", 200, OPERATIONS_READ),
                new Endpoint("GET", "/api/inventory/ledger", "/api/inventory/ledger?itemCode=NOPE", null, 400,
                        OPERATIONS_READ),

                // Reports (R15) and the dashboard. Stock and production reports follow stock access (F2)
                get("/api/reports/sales-register", 200, REPORTS),
                get("/api/reports/gst-summary", 200, REPORTS),
                get("/api/reports/purchase-register", 200, REPORTS),
                get("/api/reports/stock-summary", 200, OPERATIONS_READ),
                new Endpoint("GET", "/api/reports/stock-ledger", "/api/reports/stock-ledger?itemCode=NOPE", null, 400,
                        OPERATIONS_READ),
                get("/api/reports/production-report", 200, OPERATIONS_READ),
                get("/api/reports/collections", 200, REPORTS),
                get("/api/reports/outstanding", 200, REPORTS),
                get("/api/reports/my-sales", 200, SALESMAN),
                get("/api/dashboard", 200, DASHBOARD));
    }

    static Stream<Arguments> everyEndpointForEveryRole() {
        return endpoints().stream()
                .flatMap(endpoint -> Stream.of(UserRole.values()).map(role -> Arguments.of(endpoint, role)));
    }

    @Autowired
    MockMvc mvc;

    @Autowired
    @Qualifier("requestMappingHandlerMapping")
    RequestMappingHandlerMapping handlerMapping;

    private MockHttpServletRequestBuilder requestFor(Endpoint endpoint) {
        MockHttpServletRequestBuilder builder = request(HttpMethod.valueOf(endpoint.method()), endpoint.url());
        if (endpoint.body() != null) {
            builder.contentType(MediaType.APPLICATION_JSON).content(endpoint.body());
        }
        return builder;
    }

    @ParameterizedTest(name = "{1} -> {0}")
    @MethodSource("everyEndpointForEveryRole")
    void eachRoleIsAllowedOrForbidden(Endpoint endpoint, UserRole role) throws Exception {
        ResultActions result = mvc.perform(requestFor(endpoint).with(ApiTokens.as(mvc, USERNAMES.get(role))));
        int status = result.andReturn().getResponse().getStatus();

        if (endpoint.allowed().contains(role)) {
            assertThat(status).as("%s may call %s", role, endpoint).isEqualTo(endpoint.statusWhenAllowed());
        } else {
            assertThat(status).as("%s may not call %s", role, endpoint).isEqualTo(403);
            result.andExpect(jsonPath("$.status").value(403))
                    .andExpect(jsonPath("$.message").value("Your role is not allowed to do this"));
        }
        // Nothing was changed: every change writes an audit row, and the ledger still has only the opening stock
        assertThat(jdbc.queryForObject("SELECT count(*) FROM audit_log WHERE action <> 'LOGIN'", Integer.class))
                .isZero();
        assertThat(movementCount()).isEqualTo(6);
    }

    @ParameterizedTest(name = "no token -> {0}")
    @MethodSource("endpoints")
    void withoutATokenEveryEndpointIs401(Endpoint endpoint) throws Exception {
        mvc.perform(requestFor(endpoint))
                .andExpect(result -> assertThat(result.getResponse().getStatus()).isEqualTo(401))
                .andExpect(jsonPath("$.status").value(401));
    }

    /**
     * R2.1: @PreAuthorize alone would leave a method without it open to any logged-in user. So every endpoint
     * of the application must have one (except login), and must be in the table above.
     */
    @Test
    void everyEndpointDeclaresItsRolesAndIsInTheMatrix() {
        Set<String> mapped = new LinkedHashSet<>();
        List<String> unprotected = new ArrayList<>();
        for (Map.Entry<RequestMappingInfo, HandlerMethod> entry : handlerMapping.getHandlerMethods().entrySet()) {
            HandlerMethod handler = entry.getValue();
            if (!handler.getBeanType().getPackageName().startsWith("com.ordertoaudit")) {
                continue;   // Spring's own /error
            }
            for (var method : entry.getKey().getMethodsCondition().getMethods()) {
                for (String pattern : entry.getKey().getPathPatternsCondition().getPatternValues()) {
                    String name = method.name() + " " + pattern;
                    if (name.equals("POST /api/auth/login")) {
                        continue;
                    }
                    mapped.add(name);
                    if (!handler.hasMethodAnnotation(PreAuthorize.class)) {
                        unprotected.add(name);
                    }
                }
            }
        }
        assertThat(unprotected).as("endpoints without @PreAuthorize").isEmpty();
        assertThat(mapped).containsExactlyInAnyOrderElementsOf(endpoints().stream().map(Endpoint::toString).toList());
    }
}
