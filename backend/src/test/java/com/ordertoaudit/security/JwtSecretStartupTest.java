package com.ordertoaudit.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.time.Duration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

/**
 * R2.8: outside dev and test the application does not start without a strong JWT_SECRET. Loads the real
 * application*.yml files, but only the JWT settings, so no database is needed.
 */
class JwtSecretStartupTest {

    @Configuration
    @EnableConfigurationProperties(JwtProperties.class)
    static class OnlyJwtSettings {
    }

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withInitializer(new ConfigDataApplicationContextInitializer())
            .withUserConfiguration(OnlyJwtSettings.class);

    @BeforeEach
    void theMachineHasNoSecretOfItsOwn() {
        assumeTrue(System.getenv("JWT_SECRET") == null, "JWT_SECRET is set in this environment");
    }

    @Test
    void withNoProfileThereIsNoSecretAndStartupFails() {
        runner.run(context -> assertThat(context).hasFailed()
                .getFailure().rootCause().hasMessageContaining("JWT_SECRET"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"prod", "staging"})
    void anyOtherProfileNeedsASecret(String profile) {
        runner.withSystemProperties("spring.profiles.active=" + profile)
                .run(context -> assertThat(context).hasFailed()
                        .getFailure().rootCause().hasMessageContaining("JWT_SECRET"));
    }

    @Test
    void aShortSecretFailsAndAStrongOneStarts() {
        ApplicationContextRunner prod = runner.withSystemProperties("spring.profiles.active=prod");

        prod.withSystemProperties("JWT_SECRET=" + "s".repeat(31))
                .run(context -> assertThat(context).hasFailed());
        prod.withSystemProperties("JWT_SECRET=" + "s".repeat(32))
                .run(context -> assertThat(context).hasNotFailed());
    }

    @ParameterizedTest
    @ValueSource(strings = {"dev", "test"})
    void devAndTestHaveADefaultSecretAndAnEightHourExpiry(String profile) {
        runner.withSystemProperties("spring.profiles.active=" + profile)
                .run(context -> assertThat(context.getBean(JwtProperties.class).expiry())
                        .isEqualTo(Duration.ofHours(8)));
    }

    @Test
    void theLengthIsCountedInBytesAndTheSecretIsNeverPrinted() {
        assertThatThrownBy(() -> new JwtProperties(null, Duration.ofHours(8))).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new JwtProperties("x".repeat(32), Duration.ZERO))
                .isInstanceOf(IllegalStateException.class);
        // 16 two-byte characters are 32 bytes
        assertThat(new JwtProperties("é".repeat(16), Duration.ofHours(8)).toString())
                .contains("secret=***").doesNotContain("é");
    }
}
