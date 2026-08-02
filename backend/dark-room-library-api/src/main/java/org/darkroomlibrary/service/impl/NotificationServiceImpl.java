package org.darkroomlibrary.service.impl;

import org.darkroomlibrary.infrastructure.event.DomainEventPublisher;
import org.darkroomlibrary.infrastructure.alert.OperationalAlertService;
import org.darkroomlibrary.mapper.NotificationTaskMapper;
import org.darkroomlibrary.domain.model.NotificationTask;
import org.darkroomlibrary.service.NotificationService;
import org.darkroomlibrary.utils.MailUtil;
import org.darkroomlibrary.utils.TransactionCallbacks;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import jakarta.annotation.Resource;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
public class NotificationServiceImpl implements NotificationService {

    private static final int STATUS_PENDING = 0;
    private static final int STATUS_RETRY = 2;
    private static final int STATUS_DEAD = 4;
    private static final int PROCESSING_LEASE_MINUTES = 10;

    @Value("${middleware.rabbit.notification-routing-key:notification.task}")
    private String notificationRoutingKey;

    @Value("${notification.max-retry-count:8}")
    private int maxRetryCount;

    @Resource
    private NotificationTaskMapper notificationTaskMapper;

    @Resource
    private DomainEventPublisher domainEventPublisher;

    @Resource
    private MailUtil mailUtil;

    @Resource
    private OperationalAlertService operationalAlertService;

    @Override
    public void enqueueEmail(String receiverEmail, String subject, String content) {
        if (receiverEmail == null || receiverEmail.trim().isEmpty()) {
            log.warn("通知任务缺少收件人，已跳过: subject={}", subject);
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        NotificationTask task = NotificationTask.builder()
                .receiverEmail(receiverEmail)
                .subject(subject)
                .content(content)
                .status(STATUS_PENDING)
                .retryCount(0)
                .nextRetryTime(now)
                .createTime(now)
                .updateTime(now)
                .build();
        if (notificationTaskMapper.insert(task) != 1) {
            throw new IllegalStateException("通知任务写入失败");
        }
        publishTaskAfterCommit(task.getId());
    }

    @Override
    public void processTask(Integer taskId) {
        if (taskId == null) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        String processingToken = UUID.randomUUID().toString();
        int claimed = notificationTaskMapper.claimForProcessing(
                taskId, now, now.plusMinutes(PROCESSING_LEASE_MINUTES), processingToken);
        if (claimed == 0) {
            return;
        }
        NotificationTask task = notificationTaskMapper.getById(taskId);
        if (task == null) {
            return;
        }
        try {
            mailUtil.sendSimpleOrThrow(task.getReceiverEmail(), task.getSubject(), task.getContent());
            if (notificationTaskMapper.markSent(taskId, processingToken, LocalDateTime.now()) == 0) {
                log.warn("通知任务发送完成，但处理租约已失效: taskId={}", taskId);
            }
        } catch (Exception e) {
            int retryCount = task.getRetryCount() == null ? 1 : task.getRetryCount() + 1;
            LocalDateTime failedAt = LocalDateTime.now();
            boolean terminal = retryCount >= Math.max(1, maxRetryCount);
            int marked = notificationTaskMapper.markFailed(
                    taskId,
                    processingToken,
                    terminal ? STATUS_DEAD : STATUS_RETRY,
                    retryCount,
                    limitError(e.getMessage()),
                    terminal ? null : failedAt.plusMinutes(Math.min(30, retryCount * 5L)),
                    failedAt);
            if (marked > 0) {
                if (terminal) {
                    log.error("通知任务达到最大重试次数，已终止自动重试: taskId={}, retryCount={}, error={}",
                            taskId, retryCount, e.getMessage());
                    operationalAlertService.notificationTaskDead(task, retryCount, e.getMessage());
                } else {
                    log.warn("通知任务发送失败，等待补偿重试: taskId={}, retryCount={}, error={}",
                            taskId, retryCount, e.getMessage());
                }
            } else {
                log.warn("通知任务发送失败，但处理租约已由其他实例接管: taskId={}, error={}",
                        taskId, e.getMessage());
            }
        }
    }

    @Override
    @Scheduled(fixedDelay = 60000)
    public void retryPendingTasks() {
        List<NotificationTask> tasks = notificationTaskMapper.queryPending(LocalDateTime.now(), 20);
        for (NotificationTask task : tasks) {
            processTask(task.getId());
        }
    }

    private void publishTaskAfterCommit(Integer taskId) {
        Runnable publishTask = () -> {
            boolean published = domainEventPublisher.publish(notificationRoutingKey, taskId);
            if (!published) {
                log.info("MQ不可用，通知任务已进入数据库待补偿: taskId={}", taskId);
            }
        };
        TransactionCallbacks.afterCommit(publishTask);
    }

    private String limitError(String error) {
        if (error == null) {
            return null;
        }
        return error.length() > 500 ? error.substring(0, 500) : error;
    }
}
