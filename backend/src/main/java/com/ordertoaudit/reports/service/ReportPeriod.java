package com.ordertoaudit.reports.service;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

import com.ordertoaudit.common.Money;
import com.ordertoaudit.common.error.BadRequestException;

/** A report's date range, both days inclusive, in factory time (IST). */
public record ReportPeriod(LocalDate from, LocalDate to) {

    public static final DateTimeFormatter DISPLAY_DATE = DateTimeFormatter.ofPattern("dd-MMM-yyyy", Locale.ENGLISH);

    /** A missing date defaults to the first or last day of the current month by the business clock (R15). */
    public static ReportPeriod resolve(LocalDate from, LocalDate to, Clock businessClock) {
        LocalDate today = LocalDate.now(businessClock);
        LocalDate start = (from != null) ? from : today.withDayOfMonth(1);
        LocalDate end = (to != null) ? to : today.withDayOfMonth(today.lengthOfMonth());
        if (start.isAfter(end)) {
            throw new BadRequestException("'from' (" + start + ") must not be after 'to' (" + end + ")");
        }
        return new ReportPeriod(start, end);
    }

    /** The first instant of the period: midnight IST at the start of "from". */
    public Instant start() {
        return from.atStartOfDay(Money.ZONE).toInstant();
    }

    /** The first instant AFTER the period: midnight IST at the end of "to". */
    public Instant endExclusive() {
        return to.plusDays(1).atStartOfDay(Money.ZONE).toInstant();
    }

    /** The PostgreSQL driver binds OffsetDateTime, not Instant, for a TIMESTAMPTZ parameter. */
    public static OffsetDateTime sqlTimestamp(Instant instant) {
        return OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
    }

    public String label() {
        return from.format(DISPLAY_DATE) + " to " + to.format(DISPLAY_DATE);
    }

    /** For reports that describe a position on one day, such as outstanding. */
    public String asOfLabel() {
        return "As of " + to.format(DISPLAY_DATE);
    }
}
