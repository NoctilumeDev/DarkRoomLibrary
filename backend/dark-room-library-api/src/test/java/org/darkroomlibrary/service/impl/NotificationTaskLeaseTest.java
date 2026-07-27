package org.darkroomlibrary.service.impl;

import org.darkroomlibrary.BaseTest;
import org.darkroomlibrary.domain.model.NotificationTask;
import org.darkroomlibrary.mapper.NotificationTaskMapper;
import org.junit.jupiter.api.Test;
import org.springframework.test.annotation.DirtiesContext;

import jakarta.annotation.Resource;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;

@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class NotificationTaskLeaseTest extends BaseTest {

    @Resource
    private NotificationTaskMapper notificationTaskMapper;

    @Test
    void expiredWorkerCannotOverwriteNewLeaseOwner() {
        LocalDateTime now = LocalDateTime.now();
        NotificationTask task = NotificationTask.builder()
                .receiverEmail("lease@example.com")
                .subject("lease")
                .content("lease")
                .status(0)
                .retryCount(0)
                .nextRetryTime(now.minusMinutes(1))
                .createTime(now)
                .updateTime(now)
                .build();
        notificationTaskMapper.insert(task);

        assertEquals(1, notificationTaskMapper.claimForProcessing(
                task.getId(), now, now.minusSeconds(1), "worker-a"));
        assertEquals(1, notificationTaskMapper.claimForProcessing(
                task.getId(), now, now.plusMinutes(10), "worker-b"));

        assertEquals(0, notificationTaskMapper.markFailed(
                task.getId(), "worker-a", 2, 1, "late failure",
                now.plusMinutes(5), now.plusSeconds(1)));
        assertEquals(1, notificationTaskMapper.markSent(
                task.getId(), "worker-b", now.plusSeconds(2)));
        assertEquals(1, notificationTaskMapper.selectById(task.getId()).getStatus());
    }
}
