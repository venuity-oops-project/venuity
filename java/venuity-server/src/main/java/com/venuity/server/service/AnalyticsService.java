package com.venuity.server.service;

import com.venuity.common.MoneyUtil;
import com.venuity.server.api.dto.AnalyticsSummaryResponse;
import com.venuity.server.api.dto.DailyAnalyticsPoint;
import com.venuity.server.api.dto.DailyAnalyticsResponse;
import com.venuity.server.api.dto.HourlyAnalyticsPoint;
import com.venuity.server.api.dto.HourlyAnalyticsResponse;
import com.venuity.server.api.dto.LegacyAnalyticsResponse;
import com.venuity.server.domain.TxType;
import com.venuity.server.domain.WalletTransaction;
import com.venuity.server.repo.CustomerRepository;
import com.venuity.server.repo.WalletTransactionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;

@Service
public class AnalyticsService {

    private static final EnumSet<TxType> ANALYTICS_TYPES =
            EnumSet.of(TxType.TOPUP, TxType.OPENING_BALANCE, TxType.CHECKIN);
    private static final DateTimeFormatter DISPLAY_DAY =
            DateTimeFormatter.ofPattern("MMMM d, yyyy", Locale.US);
    private static final DateTimeFormatter DISPLAY_MONTH =
            DateTimeFormatter.ofPattern("MMMM yyyy", Locale.US);
    private static final DateTimeFormatter WEEK_LABEL =
            DateTimeFormatter.ofPattern("EEE d", Locale.US);

    private final WalletTransactionRepository ledger;
    private final CustomerRepository customers;
    private final SettingsService settings;

    public AnalyticsService(WalletTransactionRepository ledger, CustomerRepository customers,
                            SettingsService settings) {
        this.ledger = ledger;
        this.customers = customers;
        this.settings = settings;
    }

    @Transactional(readOnly = true)
    public LegacyAnalyticsResponse legacy(String interval, String metric, String date) {
        ZoneId zone = settings.zone();
        String safeInterval = normalizeInterval(interval);
        String safeMetric = normalizeMetric(metric);
        LocalDate selected = parseDate(date, zone);
        List<String> labels = new ArrayList<>();
        List<Number> data = new ArrayList<>();
        String displayDate;

        if (safeInterval.equals("hourly")) {
            displayDate = selected.format(DISPLAY_DAY);
            for (int hour = 9; hour <= 20; hour++) {
                labels.add(hour + ":00");
            }
            List<Bucket> buckets = new ArrayList<>();
            for (int hour = 9; hour <= 20; hour++) {
                buckets.add(new Bucket());
            }
            Instant from = selected.atTime(9, 0).atZone(zone).toInstant();
            Instant to = selected.atTime(21, 0).atZone(zone).toInstant();
            for (WalletTransaction transaction : ledger.findForAnalytics(from, to, ANALYTICS_TYPES)) {
                int hour = transaction.getCreatedAt().atZone(zone).getHour();
                if (hour >= 9 && hour <= 20) {
                    apply(buckets.get(hour - 9), transaction);
                }
            }
            appendData(data, safeMetric, buckets);
        } else if (safeInterval.equals("weekly")) {
            LocalDate monday = selected.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
            displayDate = monday.format(DISPLAY_DAY);
            List<Bucket> buckets = new ArrayList<>();
            for (int day = 0; day < 7; day++) {
                LocalDate dateAt = monday.plusDays(day);
                labels.add(dateAt.format(WEEK_LABEL));
                buckets.add(new Bucket());
            }
            Instant from = monday.atStartOfDay(zone).toInstant();
            Instant to = monday.plusDays(7).atStartOfDay(zone).toInstant();
            for (WalletTransaction transaction : ledger.findForAnalytics(from, to, ANALYTICS_TYPES)) {
                int index = (int) ChronoUnit.DAYS.between(monday,
                        transaction.getCreatedAt().atZone(zone).toLocalDate());
                if (index >= 0 && index < 7) {
                    apply(buckets.get(index), transaction);
                }
            }
            appendData(data, safeMetric, buckets);
        } else {
            YearMonth month = YearMonth.from(selected);
            displayDate = month.atDay(1).format(DISPLAY_MONTH);
            List<Bucket> buckets = new ArrayList<>();
            for (int day = 1; day <= month.lengthOfMonth(); day++) {
                labels.add(String.valueOf(day));
                buckets.add(new Bucket());
            }
            Instant from = month.atDay(1).atStartOfDay(zone).toInstant();
            Instant to = month.plusMonths(1).atDay(1).atStartOfDay(zone).toInstant();
            for (WalletTransaction transaction : ledger.findForAnalytics(from, to, ANALYTICS_TYPES)) {
                int index = transaction.getCreatedAt().atZone(zone).getDayOfMonth() - 1;
                if (index >= 0 && index < buckets.size()) {
                    apply(buckets.get(index), transaction);
                }
            }
            appendData(data, safeMetric, buckets);
        }
        return new LegacyAnalyticsResponse(labels, data, displayDate, safeInterval, safeMetric);
    }

    @Transactional(readOnly = true)
    public AnalyticsSummaryResponse summary(LocalDate from, LocalDate to) {
        ZoneId zone = settings.zone();
        LocalDate end = to == null ? LocalDate.now(zone) : to;
        LocalDate start = from == null ? end.minusDays(29) : from;
        if (start.isAfter(end)) {
            LocalDate swap = start;
            start = end;
            end = swap;
        }
        Instant fromInstant = start.atStartOfDay(zone).toInstant();
        Instant toInstant = end.plusDays(1).atStartOfDay(zone).toInstant();
        BigDecimal revenue = BigDecimal.ZERO;
        long checkIns = 0;
        for (WalletTransaction transaction : ledger.findForAnalytics(fromInstant, toInstant, ANALYTICS_TYPES)) {
            if (isRevenue(transaction)) {
                revenue = revenue.add(transaction.getAmount());
            } else if (transaction.getType() == TxType.CHECKIN) {
                checkIns++;
            }
        }
        long days = ChronoUnit.DAYS.between(start, end) + 1;
        return new AnalyticsSummaryResponse(start, end, MoneyUtil.normalize(revenue), checkIns,
                customers.countByActiveTrue(), days == 0 ? 0 : checkIns / (double) days);
    }

    @Transactional(readOnly = true)
    public DailyAnalyticsResponse daily(LocalDate from, LocalDate to) {
        ZoneId zone = settings.zone();
        LocalDate end = to == null ? LocalDate.now(zone) : to;
        LocalDate start = from == null ? end.minusDays(29) : from;
        if (start.isAfter(end)) {
            LocalDate swap = start;
            start = end;
            end = swap;
        }
        long days = ChronoUnit.DAYS.between(start, end) + 1;
        List<Bucket> buckets = new ArrayList<>();
        for (int index = 0; index < days; index++) {
            buckets.add(new Bucket());
        }
        Instant fromInstant = start.atStartOfDay(zone).toInstant();
        Instant toInstant = end.plusDays(1).atStartOfDay(zone).toInstant();
        for (WalletTransaction transaction : ledger.findForAnalytics(fromInstant, toInstant, ANALYTICS_TYPES)) {
            int index = (int) ChronoUnit.DAYS.between(start,
                    transaction.getCreatedAt().atZone(zone).toLocalDate());
            if (index >= 0 && index < buckets.size()) {
                apply(buckets.get(index), transaction);
            }
        }
        List<DailyAnalyticsPoint> points = new ArrayList<>();
        for (int index = 0; index < buckets.size(); index++) {
            Bucket bucket = buckets.get(index);
            points.add(new DailyAnalyticsPoint(start.plusDays(index), bucket.visits,
                    MoneyUtil.normalize(bucket.revenue)));
        }
        return new DailyAnalyticsResponse(start, end, points);
    }

    @Transactional(readOnly = true)
    public HourlyAnalyticsResponse hourly(LocalDate from, LocalDate to) {
        ZoneId zone = settings.zone();
        LocalDate end = to == null ? LocalDate.now(zone) : to;
        LocalDate start = from == null ? end.minusDays(29) : from;
        if (start.isAfter(end)) {
            LocalDate swap = start;
            start = end;
            end = swap;
        }
        Bucket[] buckets = new Bucket[24];
        for (int hour = 0; hour < 24; hour++) {
            buckets[hour] = new Bucket();
        }
        Instant fromInstant = start.atStartOfDay(zone).toInstant();
        Instant toInstant = end.plusDays(1).atStartOfDay(zone).toInstant();
        for (WalletTransaction transaction : ledger.findForAnalytics(fromInstant, toInstant, ANALYTICS_TYPES)) {
            int hour = transaction.getCreatedAt().atZone(zone).getHour();
            apply(buckets[hour], transaction);
        }
        List<HourlyAnalyticsPoint> points = new ArrayList<>();
        for (int hour = 0; hour < 24; hour++) {
            points.add(new HourlyAnalyticsPoint(hour, buckets[hour].visits,
                    MoneyUtil.normalize(buckets[hour].revenue)));
        }
        return new HourlyAnalyticsResponse(start, end, points);
    }

    private void apply(Bucket bucket, WalletTransaction transaction) {
        if (isRevenue(transaction)) {
            bucket.revenue = bucket.revenue.add(transaction.getAmount());
        } else if (transaction.getType() == TxType.CHECKIN) {
            bucket.visits++;
        }
    }

    private boolean isRevenue(WalletTransaction transaction) {
        return transaction.getType() == TxType.TOPUP || transaction.getType() == TxType.OPENING_BALANCE;
    }

    private void appendData(List<Number> data, String metric, List<Bucket> buckets) {
        for (Bucket bucket : buckets) {
            if (metric.equals("checkins")) {
                data.add(bucket.visits);
            } else {
                data.add(bucket.revenue.doubleValue());
            }
        }
    }

    private String normalizeInterval(String interval) {
        if (interval == null) {
            return "hourly";
        }
        return switch (interval.trim().toLowerCase(Locale.ROOT)) {
            case "weekly" -> "weekly";
            case "monthly" -> "monthly";
            case "daily" -> "daily";
            case "hourly" -> "hourly";
            default -> "hourly";
        };
    }

    private String normalizeMetric(String metric) {
        if (metric == null) {
            return "revenue";
        }
        return switch (metric.trim().toLowerCase(Locale.ROOT)) {
            case "checkins", "customers" -> "checkins";
            case "revenue" -> "revenue";
            default -> "revenue";
        };
    }

    private LocalDate parseDate(String raw, ZoneId zone) {
        if (raw == null || raw.isBlank()) {
            return LocalDate.now(zone);
        }
        try {
            return LocalDate.parse(raw.trim());
        } catch (RuntimeException exception) {
            return LocalDate.now(zone);
        }
    }

    private static final class Bucket {
        private BigDecimal revenue = BigDecimal.ZERO;
        private long visits;
    }
}
