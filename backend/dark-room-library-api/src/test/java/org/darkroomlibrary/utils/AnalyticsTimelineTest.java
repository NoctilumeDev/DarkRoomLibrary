package org.darkroomlibrary.utils;

import org.darkroomlibrary.web.dto.query.PageQuery;
import org.darkroomlibrary.web.view.DailyCount;
import org.darkroomlibrary.web.view.MetricPoint;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class AnalyticsTimelineTest {

    @Test
    void capsOversizedLookbackWindow() {
        PageQuery query = AnalyticsTimeline.queryWindow(Integer.MAX_VALUE);

        assertEquals(
                LocalDate.now().minusDays(3_650).atStartOfDay(),
                query.getStartTime()
        );
    }

    @Test
    void convertsDatabaseDailyCountsToChartPoints() {
        LocalDate today = LocalDate.now();
        List<MetricPoint> points = AnalyticsTimeline.toDailyMetrics(
                2,
                List.of(new DailyCount(today.minusDays(1), 3))
        );

        assertEquals(1, points.size());
        assertEquals(3, points.get(0).getCount());
        assertFalse(points.get(0).getName().isBlank());
    }
}
