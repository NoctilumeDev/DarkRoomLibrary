package org.darkroomlibrary.service.impl;

import org.darkroomlibrary.BaseTest;
import org.darkroomlibrary.web.view.DailyCount;
import org.junit.jupiter.api.Test;
import org.springframework.test.annotation.DirtiesContext;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class StatisticsAggregationTest extends BaseTest {

    @Test
    void aggregatesDailyCountsInsideDatabase() {
        LocalDateTime start = LocalDate.now().atStartOfDay();
        LocalDateTime end = start.plusDays(1);
        var user = createTestUser("daily-stats-user", "每日统计用户", "daily-stats@example.com");
        var book = createTestBook("每日统计图书", "统计作者", 2);
        createTestBorrowRecord(user.getId(), book.getId(), end.plusDays(30));

        List<DailyCount> bookCounts = bookMapper.dailyCreateStats(start, end);
        List<DailyCount> userCounts = userMapper.dailyCreateStats(start, end);
        List<DailyCount> borrowCounts = borrowRecordMapper.dailyBorrowStats(start, end);

        assertFalse(bookCounts.isEmpty());
        assertFalse(userCounts.isEmpty());
        assertFalse(borrowCounts.isEmpty());
        assertTrue(bookCounts.stream().allMatch(item -> item.getDay() != null && item.getCount() > 0));
        assertTrue(userCounts.stream().allMatch(item -> item.getDay() != null && item.getCount() > 0));
        assertTrue(borrowCounts.stream().allMatch(item -> item.getDay() != null && item.getCount() > 0));
    }
}
