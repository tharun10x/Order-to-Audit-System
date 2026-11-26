package com.ordertoaudit.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalTime;

import org.junit.jupiter.api.Test;

/** F1: the business clock is real by default, and fixed at 10:00 IST on a date when one is configured. */
class ClockConfigTest {

    private final ClockConfig config = new ClockConfig();

    @Test
    void unsetMeansTheRealClockInIst() {
        Clock clock = config.businessClock("");

        assertThat(clock.getZone()).isEqualTo(Money.ZONE);
        assertThat(LocalDate.now(clock)).isEqualTo(LocalDate.now(Money.ZONE));
        assertThat(config.businessClock(null).getZone()).isEqualTo(Money.ZONE);
    }

    @Test
    void aFixedDateIsTenOClockIstThatDay() {
        Clock clock = config.businessClock("2026-11-28");

        assertThat(LocalDate.now(clock)).isEqualTo(LocalDate.of(2026, 11, 28));
        assertThat(LocalTime.now(clock)).isEqualTo(LocalTime.of(10, 0));
    }

    @Test
    void aBadDateStopsTheApplication() {
        assertThatThrownBy(() -> config.businessClock("28/11/2026"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("OTA_BUSINESS_DATE");
    }
}
