package org.darkroomlibrary.service.impl;

import org.darkroomlibrary.infrastructure.event.DomainEventPublisher;
import org.darkroomlibrary.mapper.NotificationTaskMapper;
import org.darkroomlibrary.domain.model.NotificationTask;
import org.darkroomlibrary.service.NotificationService;
import org.darkroomlibrary.utils.MailUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import jakarta.annotation.Resource;
import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
public class NotificationServiceImpl implements NotificationService {

    private static final int STATUS_PENDING = 0;
    private static final int PROCESSING_LEASE_MINUTES = 10;

    @Value("${middleware.rabbit.notification-routing-key:notification.task}")
    private String notificationRoutingKey;

    @Resource
    private NotificationTaskMapper notificationTaskMapper;

    @Resource
    private DomainEventPublisher domainEventPublisher;

    @Resource
    private MailUtil mailUtil;

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
        notificationTaskMapper.insert(task);
        publishTaskAfterCommit(task.getId());
    }

    @Override
    public void processTask(Integer taskId) {
        if (taskId == null) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        int claimed = notificationTaskMapper.claimForProcessing(
                taskId, now, now.plusMinutes(PROCESSING_LEASE_MINUTES));
        if (claimed == 0) {
            return;
        }
        NotificationTask task = notificationTaskMapper.getById(taskId);
        if (task == null) {
            return;
        }
        try {
            mailUtil.sendSimpleOrThrow(task.getReceiverEmail(), task.getSubject(), task.getContent());
            notificationTaskMapper.markSent(taskId, LocalDateTime.now());
        } catch (Exception e) {
            int retryCount = task.getRetryCount() == null ? 1 : task.getRetryCount() + 1;
            LocalDateTime failedAt = LocalDateTime.now();
            notificationTaskMapper.markFailed(
                    taskId,
                    retryCount,
                    limitError(e.getMessage()),
                    failedAt.plusMinutes(Math.min(30, retryCount * 5L)),
                    failedAt);
            log.warn("通知任务发送失败，等待补偿重试: taskId={}, retryCount={}, error={}",
                    taskId, retryCount, e.getMessage());
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
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    publishTask.run();
                }
            });
            return;
        }
        publishTask.run();
    }

    private String limitError(String error) {
        if (error == null) {
            return null;
        }
        return error.length() > 500 ? error.substring(0, 500) : error;
    }
}
