package org.darkroomlibrary.infrastructure.alert;

import lombok.extern.slf4j.Slf4j;
import org.darkroomlibrary.infrastructure.cache.CacheService;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.core.QueueInformation;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Detects poison-message accumulation without consuming or mutating dead letters.
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "middleware.rabbit.enabled", havingValue = "true")
public class RabbitDeadLetterMonitor {

    private static final String ALERT_KEY_PREFIX = "alert:rabbit-dead-letter:";

    private final AmqpAdmin amqpAdmin;
    private final OperationalAlertService alertService;
    private final CacheService cacheService;
    private final List<String> queueNames;
    private final int alertThreshold;
    private final Duration alertCooldown;
    private final ConcurrentMap<String, Long> localAlertExpiry = new ConcurrentHashMap<>();

    public RabbitDeadLetterMonitor(
            AmqpAdmin amqpAdmin,
            OperationalAlertService alertService,
            CacheService cacheService,
            @Value("${middleware.rabbit.notification-dead-letter-queue:"
                    + "dark.room.library.notification-task.dead}") String notificationQueue,
            @Value("${middleware.rabbit.book-returned-dead-letter-queue:"
                    + "dark.room.library.book-returned.dead}") String bookReturnedQueue,
            @Value("${notification.alert.dead-letter-threshold:1}") int alertThreshold,
            @Value("${notification.alert.dead-letter-alert-cooldown-minutes:15}") long cooldownMinutes) {
        this.amqpAdmin = amqpAdmin;
        this.alertService = alertService;
        this.cacheService = cacheService;
        this.queueNames = List.of(notificationQueue, bookReturnedQueue);
        this.alertThreshold = Math.max(1, alertThreshold);
        this.alertCooldown = Duration.ofMinutes(Math.max(1L, cooldownMinutes));
    }

    @Scheduled(
            initialDelayString = "${notification.alert.dead-letter-check-initial-delay-ms:30000}",
            fixedDelayString = "${notification.alert.dead-letter-check-interval-ms:60000}")
    public void inspectDeadLetterQueues() {
        for (String queueName : queueNames) {
            inspectQueue(queueName);
        }
    }

    private void inspectQueue(String queueName) {
        try {
            QueueInformation queue = amqpAdmin.getQueueInfo(queueName);
            if (queue == null) {
                log.warn("RabbitMQ死信队列不存在或暂不可读取: queue={}", queueName);
                return;
            }
            int messageCount = queue.getMessageCount();
            if (messageCount < alertThreshold) {
                localAlertExpiry.remove(queueName);
                return;
            }
            if (!reserveAlertWindow(queueName, messageCount)) {
                return;
            }
            log.error("RabbitMQ死信队列出现积压: queue={}, messageCount={}", queueName, messageCount);
            alertService.deadLetterQueueBacklog(queueName, messageCount);
        } catch (Exception exception) {
            log.warn("RabbitMQ死信队列检查失败: queue={}, error={}",
                    queueName, exception.getMessage());
        }
    }

    private boolean reserveAlertWindow(String queueName, int messageCount) {
        String key = ALERT_KEY_PREFIX + queueName;
        long now = System.currentTimeMillis();
        long expiresAt = now + alertCooldown.toMillis();
        Optional<Boolean> distributed = cacheService.setIfAbsent(
                key,
                Integer.toString(messageCount),
                alertCooldown);
        if (distributed.isPresent()) {
            localAlertExpiry.put(queueName, expiresAt);
            return distributed.get();
        }
        AtomicBoolean reserved = new AtomicBoolean(false);
        localAlertExpiry.compute(queueName, (ignored, currentExpiry) -> {
            if (currentExpiry == null || currentExpiry <= now) {
                reserved.set(true);
                return expiresAt;
            }
            return currentExpiry;
        });
        return reserved.get();
    }
}
