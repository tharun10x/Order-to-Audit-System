package com.ordertoaudit;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import com.jayway.jsonpath.JsonPath;

/**
 * Real tokens for the API tests: logs in through POST /api/auth/login once per user and keeps the token.
 * Every test context uses the same test secret and the same seeded user ids, so one cache serves them all.
 */
public final class ApiTokens {

    /** The seeded test logins (assumption A15). */
    private static final Map<String, String> PASSWORDS = Map.of(
            "admin", "Admin@123", "production", "Prod@123", "salesman_a", "Sales@123",
            "salesman_b", "Sales@123", "auditor", "Audit@123", "owner", "Owner@123");

    private static final Map<String, String> TOKENS = new ConcurrentHashMap<>();

    private ApiTokens() {
    }

    /** Use as: mvc.perform(get("/api/orders").with(ApiTokens.as(mvc, "admin"))). */
    public static RequestPostProcessor as(MockMvc mvc, String username) {
        return bearer(token(mvc, username));
    }

    public static RequestPostProcessor bearer(String token) {
        return request -> {
            request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer " + token);
            return request;
        };
    }

    public static String token(MockMvc mvc, String username) {
        return TOKENS.computeIfAbsent(username, name -> logIn(mvc, name));
    }

    /** A new login, without the cache. */
    public static String logIn(MockMvc mvc, String username) {
        try {
            MvcResult result = mvc.perform(post("/api/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"username\": \"%s\", \"password\": \"%s\"}"
                                    .formatted(username, PASSWORDS.get(username))))
                    .andReturn();
            if (result.getResponse().getStatus() != 200) {
                throw new IllegalStateException("Login of " + username + " failed: "
                        + result.getResponse().getContentAsString());
            }
            return JsonPath.read(result.getResponse().getContentAsString(), "$.token");
        } catch (Exception e) {
            throw new IllegalStateException("Login of " + username + " failed", e);
        }
    }
}
