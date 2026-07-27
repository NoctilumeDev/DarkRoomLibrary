package org.darkroomlibrary.service.impl;

import org.darkroomlibrary.mapper.BorrowRecordMapper;
import org.darkroomlibrary.service.NotificationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import jakarta.annotation.Resource;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 还书到期提醒服务
 * 每天 8:00 检查 3 天后到期的借阅，发送一次温柔提醒
 */
@Slf4j
@Component
public class BorrowReminderService {

    private static final int REMINDER_BATCH_SIZE = 200;

    @Resource
    private BorrowRecordMapper borrowRecordMapper;

    @Resource
    private NotificationService notificationService;

    @Resource
    private PlatformTransactionManager transactionManager;

    @Value("${borrow.renew.window-days-before-due:3}")
    private int reminderDays;

    @Scheduled(cron = "0 0 8 * * ?")
    public void sendDueReminders() {
        LocalDate today = LocalDate.now();
        LocalDateTime startTime = today.atStartOfDay();
        LocalDateTime endTime = today.plusDays(reminderDays + 1L).atStartOfDay();
        int scanned = 0;
        int afterId = 0;
        while (true) {
            List<Map<String, Object>> reminders = borrowRecordMapper.findDueReminders(
                    startTime, endTime, afterId, REMINDER_BATCH_SIZE);
            if (reminders.isEmpty()) {
                break;
            }
            int nextAfterId = afterId;
            for (Map<String, Object> r : reminders) {
                Integer recordId = toInteger(getValue(r, "recordId"));
                if (recordId != null) {
                    nextAfterId = Math.max(nextAfterId, recordId);
                }
                String email = toString(getValue(r, "userEmail"));
                String userName = toString(getValue(r, "userName"));
                String bookName = toString(getValue(r, "bookName"));
                LocalDateTime dueDate = toLocalDateTime(getValue(r, "dueDate"));
                if (recordId == null || email == null || email.isEmpty()) {
                    continue;
                }
                try {
                    enqueueReminderOnce(recordId, email, userName, bookName, dueDate);
                } catch (Exception e) {
                    log.warn("还书提醒入队失败: recordId={}, email={}, book={}, error={}",
                            recordId, email, bookName, e.getMessage());
                }
            }
            scanned += reminders.size();
            if (reminders.size() < REMINDER_BATCH_SIZE || nextAfterId <= afterId) {
                break;
            }
            afterId = nextAfterId;
        }
        if (scanned > 0) {
            log.info("已扫描 {} 条即将到期的借阅提醒", scanned);
        }
    }

    private void enqueueReminderOnce(Integer recordId,
                                     String email,
                                     String userName,
                                     String bookName,
                                     LocalDateTime dueDate) {
        TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);
        transactionTemplate.executeWithoutResult(status -> {
            if (borrowRecordMapper.markDueReminderSent(recordId, LocalDateTime.now()) == 0) {
                return;
            }
            String dueDateText = dueDate == null ? "近期" : dueDate.toLocalDate().toString();
            notificationService.enqueueEmail(
                    email,
                    "【暗室藏书】还书提醒",
                    userName + "，你好。\n\n"
                            + "你借阅的《" + bookName + "》将于 " + dueDateText + " 到期。\n"
                            + "到期前 " + reminderDays + " 天内可以续借，请及时处理。\n\n"
                            + "—— 暗室藏书"
            );
        });
    }

    private Object getValue(Map<String, Object> row, String key) {
        Object value = row.get(key);
        if (value != null) {
            return value;
        }
        for (Map.Entry<String, Object> entry : row.entrySet()) {
            if (entry.getKey() != null && entry.getKey().equalsIgnoreCase(key)) {
                return entry.getValue();
            }
        }
        return null;
    }

    private String toString(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private Integer toInteger(Object value) {
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        if (value instanceof String) {
            return Integer.valueOf((String) value);
        }
        return null;
    }

    private LocalDateTime toLocalDateTime(Object value) {
        if (value instanceof LocalDateTime) {
            return (LocalDateTime) value;
        }
        if (value instanceof Timestamp) {
            return ((Timestamp) value).toLocalDateTime();
        }
        if (value instanceof LocalDate) {
            return ((LocalDate) value).atStartOfDay();
        }
        return null;
    }
}
