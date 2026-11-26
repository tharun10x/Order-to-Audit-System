package com.ordertoaudit.security;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import com.ordertoaudit.AbstractLedgerTest;
import com.ordertoaudit.ApiTokens;

/** R2.5: the lifetime is configurable, and a token stops working the moment it expires. */
@AutoConfigureMockMvc
@TestPropertySource(properties = "ota.security.jwt.expiry=3s")
class TokenExpiryTest extends AbstractLedgerTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    JwtDecoder jwtDecoder;

    @Test
    void anExpiredTokenIs401() throws Exception {
        // Not the cached login: this one must come from this 3-second configuration
        String token = ApiTokens.logIn(mvc, "admin");
        Instant expiresAt = jwtDecoder.decode(token).getExpiresAt();
        mvc.perform(get("/api/auth/me").with(ApiTokens.bearer(token))).andExpect(status().isOk());

        Thread.sleep(Duration.between(Instant.now(), expiresAt).plusMillis(500));

        mvc.perform(get("/api/auth/me").with(ApiTokens.bearer(token)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
    }
}
