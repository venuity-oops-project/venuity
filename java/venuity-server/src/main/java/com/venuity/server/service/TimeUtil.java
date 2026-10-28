package com.venuity.server.service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

public final class TimeUtil {

    private TimeUtil() {
    }

    public static String legacy(Instant instant, ZoneId zone) {
        if (instant == null) {
            return null;
        }
        return instant.atZone(zone).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
    }

    public static Instant startOfDay(LocalDate date, ZoneId zone) {
        return date.atStartOfDay(zone).toInstant();
    }

    public static Instant startOfNextDay(LocalDate date, ZoneId zone) {
        return date.plusDays(1).atStartOfDay(zone).toInstant();
    }
}
