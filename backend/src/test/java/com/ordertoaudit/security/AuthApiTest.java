package com.ordertoaudit.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.jayway.jsonpath.JsonPath;
import com.ordertoaudit.AbstractLedgerTest;
import com.ordertoaudit.ApiTokens;

/** POST /api/auth/login and GET /api/auth/me (R2.4, R2.5, R14.4). */
@AutoConfigureMockMvc
class AuthApiTest extends AbstractLedgerTest {

    private static final String FAILED_MESSAGE = "Invalid username or password";

    @Autowired
    MockMvc mvc;

    @Autowired
    JwtDecoder jwtDecoder;

    private ResultActions logIn(String username, String password) throws Exception {
        return mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\": \"%s\", \"password\": \"%s\"}".formatted(username, password)));
    }

    private String body(ResultActions result) throws Exception {
        return result.andReturn().getResponse().getContentAsString();
    }

    private Long userId(String username) {
        return jdbc.queryForObject("SELECT id FROM app_user WHERE username = ?", Long.class, username);
    }

    private List<Map<String, Object>> auditRows() {
        return jdbc.queryForList(
                "SELECT entity_type, entity_id, action, user_id, username FROM audit_log ORDER BY id");
    }

    @Test
    void aCorrectPasswordGivesATokenWithTheUserIdUsernameAndRole() throws Exception {
        String response = body(logIn("admin", "Admin@123")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.id").value(userId("admin").intValue()))
                .andExpect(jsonPath("$.user.username").value("admin"))
                .andExpect(jsonPath("$.user.fullName").value("Admin"))
                .andExpect(jsonPath("$.user.role").value("ADMIN"))
                .andExpect(jsonPath("$.user.passwordHash").doesNotExist())
                .andExpect(jsonPath("$.businessDate").value("2026-11-28")));

        Jwt token = jwtDecoder.decode(JsonPath.read(response, "$.token"));
        assertThat(token.getHeaders().get("alg")).isEqualTo("HS256");
        assertThat(token.getSubject()).isEqualTo(String.valueOf(userId("admin")));
        assertThat(token.getClaimAsString("username")).isEqualTo("admin");
        assertThat(token.getClaimAsString("role")).isEqualTo("ADMIN");
        assertThat(Duration.between(token.getIssuedAt(), token.getExpiresAt())).isEqualTo(Duration.ofHours(8));
        assertThat(Instant.parse(JsonPath.read(response, "$.expiresAt"))).isEqualTo(token.getExpiresAt());
    }

    @Test
    void aSuccessfulLoginIsAudited() throws Exception {
        logIn("auditor", "Audit@123").andExpect(status().isOk());

        assertThat(auditRows()).singleElement().satisfies(row -> {
            assertThat(row.get("action")).isEqualTo("LOGIN");
            assertThat(row.get("entity_type")).isEqualTo("USER");
            assertThat(row.get("entity_id")).isEqualTo(userId("auditor"));
            assertThat(row.get("user_id")).isEqualTo(userId("auditor"));
            assertThat(row.get("username")).isEqualTo("auditor");
        });
    }

    @Test
    void aWrongPasswordAndAnUnknownUserGetTheSame401() throws Exception {
        String wrongPassword = body(logIn("admin", "wrong").andExpect(status().isUnauthorized()));
        String unknownUser = body(logIn("nobody", "Admin@123").andExpect(status().isUnauthorized()));

        assertThat(wrongPassword).isEqualTo(unknownUser);
        assertThat((String) JsonPath.read(wrongPassword, "$.message")).isEqualTo(FAILED_MESSAGE);
        assertThat(wrongPassword).doesNotContain("token");
    }

    /** R14.4: the row survives although the request failed, and names nobody but the attempted username. */
    @Test
    void failedLoginsAreAuditedWithTheUsernameOnly() throws Exception {
        logIn("admin", "wrong").andExpect(status().isUnauthorized());
        logIn("nobody", "whatever").andExpect(status().isUnauthorized());

        assertThat(auditRows()).hasSize(2).allSatisfy(row -> {
            assertThat(row.get("action")).isEqualTo("LOGIN_FAILED");
            assertThat(row.get("entity_type")).isEqualTo("USER");
            assertThat(row.get("user_id")).isNull();
            assertThat(row.get("entity_id")).isNull();
        }).extracting(row -> row.get("username")).containsExactly("admin", "nobody");
    }

    @Test
    void anInactiveUserCannotLogIn() throws Exception {
        jdbc.update("UPDATE app_user SET active = FALSE WHERE username = 'auditor'");
        try {
            logIn("auditor", "Audit@123")
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.message").value(FAILED_MESSAGE));

            assertThat(auditRows()).singleElement()
                    .satisfies(row -> assertThat(row.get("action")).isEqualTo("LOGIN_FAILED"));
        } finally {
            jdbc.update("UPDATE app_user SET active = TRUE WHERE username = 'auditor'");
        }
    }

    /** R2.7: checked on every request, also on endpoints that never ask who the user is. */
    @Test
    void deactivatingAUserStopsTheirExistingToken() throws Exception {
        String token = ApiTokens.logIn(mvc, "auditor");
        mvc.perform(get("/api/inventory/balances").with(ApiTokens.bearer(token))).andExpect(status().isOk());

        jdbc.update("UPDATE app_user SET active = FALSE WHERE username = 'auditor'");
        try {
            mvc.perform(get("/api/inventory/balances").with(ApiTokens.bearer(token)))
                    .andExpect(status().isUnauthorized());
            mvc.perform(get("/api/auth/me").with(ApiTokens.bearer(token))).andExpect(status().isUnauthorized());
        } finally {
            jdbc.update("UPDATE app_user SET active = TRUE WHERE username = 'auditor'");
        }
    }

    @Test
    void changingAUsersRoleStopsTheirExistingToken() throws Exception {
        String token = ApiTokens.logIn(mvc, "owner");

        jdbc.update("UPDATE app_user SET role = 'AUDITOR' WHERE username = 'owner'");
        try {
            mvc.perform(get("/api/auth/me").with(ApiTokens.bearer(token))).andExpect(status().isUnauthorized());
        } finally {
            jdbc.update("UPDATE app_user SET role = 'OWNER' WHERE username = 'owner'");
        }
    }

    @ParameterizedTest
    @CsvSource({"admin, ADMIN", "production, PRODUCTION", "salesman_a, SALESMAN", "salesman_b, SALESMAN",
            "auditor, AUDITOR", "owner, OWNER"})
    void meReturnsTheUserOfTheToken(String username, String role) throws Exception {
        mvc.perform(get("/api/auth/me").with(ApiTokens.as(mvc, username)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(userId(username).intValue()))
                .andExpect(jsonPath("$.username").value(username))
                .andExpect(jsonPath("$.role").value(role))
                .andExpect(jsonPath("$.businessDate").value("2026-11-28"))
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
    }

    @Test
    void badLoginInput() throws Exception {
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
        // 51 characters would not fit audit_log.username
        logIn("u".repeat(51), "x").andExpect(status().isBadRequest());
        // BCrypt reads only 72 bytes: longer input is a wrong password, not a server error
        logIn("admin", "Admin@123" + "x".repeat(80)).andExpect(status().isUnauthorized());
    }

    @Test
    void loginIgnoresAStaleTokenInTheHeader() throws Exception {
        mvc.perform(post("/api/auth/login").with(ApiTokens.bearer("expired.or.garbage"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\": \"admin\", \"password\": \"Admin@123\"}"))
                .andExpect(status().isOk());
    }
}
