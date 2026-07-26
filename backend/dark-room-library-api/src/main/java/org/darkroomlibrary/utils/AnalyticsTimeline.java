package org.darkroomlibrary.utils;

import org.darkroomlibrary.web.dto.query.PageQuery;
import org.darkroomlibrary.web.view.MetricPoint;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Builds date windows and chart-ready daily counts for reporting endpoints.
 */
public final class AnalyticsTimeline {

    private AnalyticsTimeline() {
    }

    public static PageQuery queryWindow(Integer lookbackDays) {
        if (Objects.equals(lookbackDays, -1)) {
            return new PageQuery();
        }

        int days = Math.max(lookbackDays == null ? 0 : lookbackDays, 0);
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime start = now.toLocalDate().minusDays(days).atStartOfDay();
        return PageQuery.builder()
                .startTime(start)
                .endTime(now)
                .build();
    }

    public static List<MetricPoint> toDailyMetrics(Integer lookbackDays, List<LocalDateTime> timestamps) {
        int days = Math.max(lookbackDays == null ? 0 : lookbackDays, 0);
        LocalDate firstDay = LocalDate.now().minusDays(days);
        LocalDate lastDay = LocalDate.now();
        Map<LocalDate, Integer> counts = new LinkedHashMap<>();

        for (LocalDate day = firstDay; !day.isAfter(lastDay); day = day.plusDays(1)) {
            counts.put(day, 0);
        }

        List<LocalDateTime> safeTimestamps = timestamps == null ? List.of() : timestamps;
        for (LocalDateTime timestamp : safeTimestamps) {
            if (timestamp == null) {
                continue;
            }
            counts.computeIfPresent(timestamp.toLocalDate(), (ignored, count) -> count + 1);
        }

        return counts.entrySet().stream()
                .filter(entry -> entry.getValue() > 0)
                .map(entry -> new MetricPoint(formatDay(entry.getKey()), entry.getValue()))
                .collect(Collectors.toList());
    }

    private static String formatDay(LocalDate day) {
        return String.format("%02d-%02d", day.getMonthValue(), day.getDayOfMonth());
    }
}
