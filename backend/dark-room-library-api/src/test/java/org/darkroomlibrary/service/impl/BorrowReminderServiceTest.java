package org.darkroomlibrary.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import org.darkroomlibrary.BaseTest;
import org.darkroomlibrary.domain.model.Book;
import org.darkroomlibrary.domain.model.BorrowRecord;
import org.darkroomlibrary.domain.model.NotificationTask;
import org.darkroomlibrary.domain.model.User;
import org.darkroomlibrary.mapper.NotificationTaskMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.annotation.DirtiesContext;

import jakarta.annotation.Resource;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class BorrowReminderServiceTest extends BaseTest {

    @Resource
    private BorrowReminderService borrowReminderService;

    @Resource
    private NotificationTaskMapper notificationTaskMapper;

    @Test
    @DisplayName("并发提醒任务只创建一条通知")
    void concurrentReminderRunsEnqueueOnce() throws Exception {
        String suffix = String.valueOf(System.nanoTime());
        String email = "reminder_" + suffix + "@example.test";
        User user = createTestUser("reminder_" + suffix, "提醒读者", email);
        Book book = createTestBook("提醒图书-" + suffix, "提醒作者", 1);
        BorrowRecord record = createTestBorrowRecord(
                user.getId(),
                book.getId(),
                LocalDate.now().plusDays(3).atTime(12, 0));

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        Future<?> first = executor.submit(() -> runReminder(start));
        Future<?> second = executor.submit(() -> runReminder(start));
        start.countDown();
        first.get(10, TimeUnit.SECONDS);
        second.get(10, TimeUnit.SECONDS);
        executor.shutdownNow();

        assertNotNull(borrowRecordMapper.getById(record.getId()).getDueReminderSentTime());
        List<NotificationTask> tasks = notificationTaskMapper.selectList(
                Wrappers.<NotificationTask>lambdaQuery()
                        .eq(NotificationTask::getReceiverEmail, email)
                        .eq(NotificationTask::getSubject, "【暗室藏书】还书提醒"));
        assertEquals(1, tasks.size());
    }

    @Test
    @DisplayName("错过精确提醒日后仍会补扫未来到期记录")
    void catchUpWindowIncludesBorrowDueSoonerThanConfiguredDays() {
        String suffix = String.valueOf(System.nanoTime());
        String email = "reminder_catchup_" + suffix + "@example.test";
        User user = createTestUser("reminder_catchup_" + suffix, "补扫读者", email);
        Book book = createTestBook("补扫图书-" + suffix, "补扫作者", 1);
        BorrowRecord record = createTestBorrowRecord(
                user.getId(),
                book.getId(),
                LocalDate.now().plusDays(1).atTime(12, 0));

        borrowReminderService.sendDueReminders();

        assertNotNull(borrowRecordMapper.getById(record.getId()).getDueReminderSentTime());
        List<NotificationTask> tasks = notificationTaskMapper.selectList(
                Wrappers.<NotificationTask>lambdaQuery()
                        .eq(NotificationTask::getReceiverEmail, email)
                        .eq(NotificationTask::getSubject, "【暗室藏书】还书提醒"));
        assertEquals(1, tasks.size());
    }

    private void runReminder(CountDownLatch start) {
        try {
            start.await();
            borrowReminderService.sendDueReminders();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }
}
