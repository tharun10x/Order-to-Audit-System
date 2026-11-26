package com.ordertoaudit.common;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * The business clock: what "today" is for order dates, default credit-note dates, default report periods and
 * the dashboard. Normally the real clock in IST. Setting ota.clock.fixed-date (env OTA_BUSINESS_DATE) pins it
 * to 10:00 IST on that date, so a demo or a test always sees the same "today". The test profile pins it to
 * 2026-11-28. Security timestamps (token expiry, posted-at) always use the real time.
 */
@Configuration
public class ClockConfig {

    private static final Logger log = LoggerFactory.getLogger(ClockConfig.class);
    private static final LocalTime FIXED_TIME = LocalTime.of(10, 0);

    @Bean
    Clock businessClock(@Value("${ota.clock.fixed-date:}") String fixedDate) {
        if (fixedDate == null || fixedDate.isBlank()) {
            return Clock.system(Money.ZONE);
        }
        LocalDate date;
        try {
            date = LocalDate.parse(fixedDate.strip());
        } catch (DateTimeParseException e) {
            throw new IllegalStateException("ota.clock.fixed-date (OTA_BUSINESS_DATE) must be a date like 2026-11-28,"
                    + " got '" + fixedDate + "'", e);
        }
        log.warn("Business date is FIXED at {} {} IST (ota.clock.fixed-date / OTA_BUSINESS_DATE). Unset it for real use",
                date, FIXED_TIME);
        return Clock.fixed(date.atTime(FIXED_TIME).atZone(Money.ZONE).toInstant(), Money.ZONE);
    }
}
