package com.ordertoaudit.security;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;

import javax.crypto.spec.SecretKeySpec;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import com.ordertoaudit.AbstractLedgerTest;
import com.ordertoaudit.ApiTokens;

/** Which tokens are refused, plus CORS and the public surface (R2.6). Expiry is in TokenExpiryTest. */
@AutoConfigureMockMvc
class TokenSecurityTest extends AbstractLedgerTest {

    private static final String DEV_ORIGIN = "http://localhost:5173";

    @Autowired
    MockMvc mvc;

    @Autowired
    JwtEncoder applicationEncoder;

    private ResultActions callMe(String token) throws Exception {
        return mvc.perform(get("/api/auth/me").with(ApiTokens.bearer(token)));
    }

    private void assertRefused(String token) throws Exception {
        callMe(token)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.message").value("A valid login token is required"));
    }

    private JwtClaimsSet.Builder adminClaims() {
        Long adminId = jdbc.queryForObject("SELECT id FROM app_user WHERE username = 'admin'", Long.class);
        return JwtClaimsSet.builder().subject(String.valueOf(adminId)).claim("username", "admin")
                .claim("role", "ADMIN").issuedAt(Instant.now());
    }

    private String sign(JwtEncoder encoder, JwtClaimsSet claims) {
        return encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
                .getTokenValue();
    }

    private static String base64(String json) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(json.getBytes(StandardCharsets.UTF_8));
    }

    private static String decode(String part) {
        return new String(Base64.getUrlDecoder().decode(part), StandardCharsets.UTF_8);
    }

    @Test
    void noTokenIs401WithAJsonBody() throws Exception {
        mvc.perform(get("/api/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, "Bearer"))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.error").value("Unauthorized"))
                .andExpect(jsonPath("$.details").isArray());
        assertRefused("not-a-token");
    }

    /** The test's own signing helper makes tokens that work, so the refusals below are for the stated reason. */
    @Test
    void aProperlySignedTokenIsAccepted() throws Exception {
        callMe(sign(applicationEncoder, adminClaims().expiresAt(Instant.now().plusSeconds(60)).build()))
                .andExpect(status().isOk());
    }

    @Test
    void aTokenWithAChangedRoleIs401() throws Exception {
        String[] parts = ApiTokens.token(mvc, "salesman_a").split("\\.");
        String forged = parts[0] + "." + base64(decode(parts[1]).replace("SALESMAN", "ADMIN")) + "." + parts[2];

        assertRefused(forged);
    }

    @Test
    void aTokenWithAChangedSignatureIs401() throws Exception {
        String token = ApiTokens.token(mvc, "admin");
        // The first character of the signature (the last one can hold unused bits)
        int position = token.lastIndexOf('.') + 1;
        char replacement = token.charAt(position) == 'A' ? 'B' : 'A';

        assertRefused(token.substring(0, position) + replacement + token.substring(position + 1));
    }

    @Test
    void aTokenSignedWithAnotherSecretIs401() throws Exception {
        JwtEncoder attacker = new NimbusJwtEncoder(new ImmutableSecret<>(new SecretKeySpec(
                "some-other-secret-that-is-long-enough".getBytes(StandardCharsets.UTF_8), "HmacSHA256")));

        assertRefused(sign(attacker, adminClaims().expiresAt(Instant.now().plusSeconds(60)).build()));
    }

    @Test
    void anUnsignedTokenIs401() throws Exception {
        String[] parts = ApiTokens.token(mvc, "admin").split("\\.");

        assertRefused(base64("{\"alg\":\"none\"}") + "." + parts[1] + ".");
    }

    @Test
    void aSignedTokenWithoutAnExpiryIs401() throws Exception {
        assertRefused(sign(applicationEncoder, adminClaims().build()));
    }

    @Test
    void aSignedTokenForNoSuchUserIs401() throws Exception {
        for (String subject : new String[] {"999999", "admin"}) {
            assertRefused(sign(applicationEncoder,
                    adminClaims().subject(subject).expiresAt(Instant.now().plusSeconds(60)).build()));
        }
    }

    @Test
    void onlyLoginIsPublic() throws Exception {
        mvc.perform(get("/error")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/no-such-endpoint")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/no-such-endpoint").with(ApiTokens.as(mvc, "admin"))).andExpect(status().isNotFound());
        mvc.perform(get("/api/auth/login")).andExpect(status().isUnauthorized());   // only POST is public
    }

    @Test
    void theViteDevServerMayCallTheApiFromTheBrowser() throws Exception {
        // The browser's preflight carries no token
        mvc.perform(options("/api/orders")
                        .header(HttpHeaders.ORIGIN, DEV_ORIGIN)
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "authorization,content-type"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, DEV_ORIGIN));
        // A 401 must carry the CORS header too, or the frontend could not read it
        mvc.perform(get("/api/orders").header(HttpHeaders.ORIGIN, DEV_ORIGIN))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, DEV_ORIGIN));
    }

    @Test
    void otherOriginsAreRefused() throws Exception {
        mvc.perform(options("/api/orders")
                        .header(HttpHeaders.ORIGIN, "https://evil.example")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET"))
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));
    }
}
