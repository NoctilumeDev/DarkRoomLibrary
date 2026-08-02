package org.darkroomlibrary.infrastructure.alert;

import org.darkroomlibrary.infrastructure.cache.CacheService;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.core.QueueInformation;

import java.util.Optional;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RabbitDeadLetterMonitorTest {

    @Test
    void alertsOnceWhenDeadLetterThresholdIsReached() {
        AmqpAdmin amqpAdmin = mock(AmqpAdmin.class);
        OperationalAlertService alertService = mock(OperationalAlertService.class);
        CacheService cacheService = mock(CacheService.class);
        when(amqpAdmin.getQueueInfo("notification.dead"))
                .thenReturn(new QueueInformation("notification.dead", 2, 0));
        when(amqpAdmin.getQueueInfo("book.dead"))
                .thenReturn(new QueueInformation("book.dead", 0, 0));
        when(cacheService.setIfAbsent(
                "alert:rabbit-dead-letter:notification.dead",
                "2",
                java.time.Duration.ofMinutes(15)))
                .thenReturn(Optional.of(true));

        RabbitDeadLetterMonitor monitor = new RabbitDeadLetterMonitor(
                amqpAdmin,
                alertService,
                cacheService,
                "notification.dead",
                "book.dead",
                1,
                15);

        monitor.inspectDeadLetterQueues();

        verify(alertService).deadLetterQueueBacklog("notification.dead", 2);
        verify(alertService, never()).deadLetterQueueBacklog("book.dead", 0);
    }

    @Test
    void suppressesDuplicateAlertWhenAnotherInstanceOwnsCooldown() {
        AmqpAdmin amqpAdmin = mock(AmqpAdmin.class);
        OperationalAlertService alertService = mock(OperationalAlertService.class);
        CacheService cacheService = mock(CacheService.class);
        when(amqpAdmin.getQueueInfo("notification.dead"))
                .thenReturn(new QueueInformation("notification.dead", 3, 0));
        when(amqpAdmin.getQueueInfo("book.dead"))
                .thenReturn(new QueueInformation("book.dead", 0, 0));
        when(cacheService.setIfAbsent(
                "alert:rabbit-dead-letter:notification.dead",
                "3",
                java.time.Duration.ofMinutes(15)))
                .thenReturn(Optional.of(false));

        RabbitDeadLetterMonitor monitor = new RabbitDeadLetterMonitor(
                amqpAdmin,
                alertService,
                cacheService,
                "notification.dead",
                "book.dead",
                1,
                15);

        monitor.inspectDeadLetterQueues();

        verify(alertService, never()).deadLetterQueueBacklog("notification.dead", 3);
    }

    @Test
    void localFallbackStillSuppressesRepeatedAlerts() {
        AmqpAdmin amqpAdmin = mock(AmqpAdmin.class);
        OperationalAlertService alertService = mock(OperationalAlertService.class);
        CacheService cacheService = mock(CacheService.class);
        when(amqpAdmin.getQueueInfo("notification.dead"))
                .thenReturn(new QueueInformation("notification.dead", 4, 0));
        when(amqpAdmin.getQueueInfo("book.dead"))
                .thenReturn(new QueueInformation("book.dead", 0, 0));
        when(cacheService.setIfAbsent(
                "alert:rabbit-dead-letter:notification.dead",
                "4",
                java.time.Duration.ofMinutes(15)))
                .thenReturn(Optional.empty());

        RabbitDeadLetterMonitor monitor = new RabbitDeadLetterMonitor(
                amqpAdmin,
                alertService,
                cacheService,
                "notification.dead",
                "book.dead",
                1,
                15);

        monitor.inspectDeadLetterQueues();
        monitor.inspectDeadLetterQueues();

        verify(alertService).deadLetterQueueBacklog("notification.dead", 4);
    }

    @Test
    void distributedSuppressionSurvivesImmediateRedisFallback() {
        AmqpAdmin amqpAdmin = mock(AmqpAdmin.class);
        OperationalAlertService alertService = mock(OperationalAlertService.class);
        CacheService cacheService = mock(CacheService.class);
        when(amqpAdmin.getQueueInfo("notification.dead"))
                .thenReturn(new QueueInformation("notification.dead", 5, 0));
        when(amqpAdmin.getQueueInfo("book.dead"))
                .thenReturn(new QueueInformation("book.dead", 0, 0));
        when(cacheService.setIfAbsent(
                "alert:rabbit-dead-letter:notification.dead",
                "5",
                java.time.Duration.ofMinutes(15)))
                .thenReturn(Optional.of(false))
                .thenReturn(Optional.empty());

        RabbitDeadLetterMonitor monitor = new RabbitDeadLetterMonitor(
                amqpAdmin,
                alertService,
                cacheService,
                "notification.dead",
                "book.dead",
                1,
                15);

        monitor.inspectDeadLetterQueues();
        monitor.inspectDeadLetterQueues();

        verify(alertService, never()).deadLetterQueueBacklog("notification.dead", 5);
    }
}
