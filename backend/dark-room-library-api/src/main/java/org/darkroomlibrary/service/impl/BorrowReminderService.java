package org.darkroomlibrary.service.impl;

import org.darkroomlibrary.mapper.BorrowRecordMapper;
import org.darkroomlibrary.service.NotificationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import jakarta.annotation.Resource;
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

    @Resource
    private BorrowRecordMapper borrowRecordMapper;

    @Resource
    private NotificationService notificationService;

    @Value("${borrow.renew.window-days-before-due:3}")
    private int reminderDays;

    @Scheduled(cron = "0 0 8 * * ?")
    public void sendDueReminders() {
        LocalDate targetDate = LocalDate.now().plusDays(reminderDays);
        LocalDateTime startTime = targetDate.atStartOfDay();
        LocalDateTime endTime = targetDate.plusDays(1).atStartOfDay();
        List<Map<String, Object>> reminders = borrowRecordMapper.findDueReminders(startTime, endTime);
        if (reminders.isEmpty()) {
            return;
        }
        log.info("发现 {} 条即将到期的借阅，准备发送提醒", reminders.size());
        for (Map<String, Object> r : reminders) {
            Integer recordId = toInteger(getValue(r, "recordId"));
            String email = toString(getValue(r, "userEmail"));
            String userName = toString(getValue(r, "userName"));
            String bookName = toString(getValue(r, "bookName"));
            if (recordId == null || email == null || email.isEmpty()) {
                continue;
            }
            try {
                notificationService.enqueueEmail(
                        email,
                        "【暗室图书馆】还书提醒",
                        userName + "，你好。\n\n"
                                + "你借阅的《" + bookName + "》将于 " + reminderDays + " 天后到期。\n"
                                + "到期前 " + reminderDays + " 天内可以续借，请及时处理。\n\n"
                                + "—— 暗室图书馆"
                );
                borrowRecordMapper.markDueReminderSent(recordId, LocalDateTime.now());
            } catch (Exception e) {
                log.warn("还书提醒发送失败: email={}, book={}", email, bookName);
            }
        }
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
}
