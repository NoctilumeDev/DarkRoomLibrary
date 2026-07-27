package org.darkroomlibrary.utils;

import org.darkroomlibrary.web.dto.query.PageQuery;
import org.darkroomlibrary.web.view.DailyCount;
import org.darkroomlibrary.web.view.MetricPoint;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Builds date windows and chart-ready daily counts for reporting endpoints.
 */
public final class AnalyticsTimeline {

    private static final int MAX_LOOKBACK_DAYS = 3_650;

    private AnalyticsTimeline() {
    }

    public static PageQuery queryWindow(Integer lookbackDays) {
        int days = normalizeDays(lookbackDays);
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime start = now.toLocalDate().minusDays(days).atStartOfDay();
        return PageQuery.builder()
                .startTime(start)
                .endTime(now)
                .build();
    }

    public static List<MetricPoint> toDailyMetrics(Integer lookbackDays, List<DailyCount> dailyCounts) {
        int days = normalizeDays(lookbackDays);
        LocalDate firstDay = LocalDate.now().minusDays(days);
        LocalDate lastDay = LocalDate.now();
        Map<LocalDate, Integer> counts = new LinkedHashMap<>();

        for (LocalDate day = firstDay; !day.isAfter(lastDay); day = day.plusDays(1)) {
            counts.put(day, 0);
        }

        List<DailyCount> safeCounts = dailyCounts == null ? List.of() : dailyCounts;
        for (DailyCount dailyCount : safeCounts) {
            if (dailyCount == null || dailyCount.getDay() == null) {
                continue;
            }
            counts.computeIfPresent(
                    dailyCount.getDay(),
                    (ignored, count) -> count + Math.max(0, dailyCount.getCount() == null ? 0 : dailyCount.getCount())
            );
        }

        return counts.entrySet().stream()
                .filter(entry -> entry.getValue() > 0)
                .map(entry -> new MetricPoint(formatDay(entry.getKey()), entry.getValue()))
                .collect(Collectors.toList());
    }

    private static int normalizeDays(Integer lookbackDays) {
        if (lookbackDays == null || lookbackDays < 0) {
            return 0;
        }
        return Math.min(lookbackDays, MAX_LOOKBACK_DAYS);
    }

    private static String formatDay(LocalDate day) {
        return String.format("%02d-%02d", day.getMonthValue(), day.getDayOfMonth());
    }
}
