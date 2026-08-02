package org.darkroomlibrary.config;

import org.darkroomlibrary.domain.model.OperationLog;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertEquals;

class RabbitEventConfigTest {

    @Test
    void messageConverterSupportsOperationLogWithLocalDateTime() {
        ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        MessageConverter converter = new RabbitEventConfig().messageConverter(objectMapper);
        LocalDateTime createTime = LocalDateTime.of(2026, 7, 13, 17, 30);
        OperationLog payload = OperationLog.builder()
                .userId(1)
                .userName("acceptance")
                .operation("test")
                .target("rabbitmq")
                .createTime(createTime)
                .build();

        Message message = converter.toMessage(payload, new MessageProperties());
        Object decoded = converter.fromMessage(message);

        OperationLog operationLog = assertInstanceOf(OperationLog.class, decoded);
        assertEquals(payload.getUserId(), operationLog.getUserId());
        assertEquals(payload.getOperation(), operationLog.getOperation());
        assertEquals(createTime, operationLog.getCreateTime());
    }

    @Test
    void keepsExistingMainQueueDeclarationsCompatibleWhileDeclaringDeadQueues() {
        RabbitEventConfig config = new RabbitEventConfig();
        ReflectionTestUtils.setField(config, "notificationTaskQueueName", "notification.main");
        ReflectionTestUtils.setField(config, "bookReturnedQueueName", "book.main");
        ReflectionTestUtils.setField(config, "deadLetterExchangeName", "events.dead");
        ReflectionTestUtils.setField(config, "notificationDeadLetterQueueName", "notification.dead.queue");
        ReflectionTestUtils.setField(config, "bookReturnedDeadLetterQueueName", "book.dead.queue");
        ReflectionTestUtils.setField(config, "notificationDeadLetterRoutingKey", "dead.notification");
        ReflectionTestUtils.setField(config, "bookReturnedDeadLetterRoutingKey", "dead.book");

        Queue notificationQueue = config.notificationTaskQueue();
        Queue bookQueue = config.bookReturnedQueue();
        Queue notificationDeadQueue = config.notificationDeadLetterQueue();
        Queue bookDeadQueue = config.bookReturnedDeadLetterQueue();

        assertEquals("notification.main", notificationQueue.getName());
        assertEquals("book.main", bookQueue.getName());
        assertEquals(0, notificationQueue.getArguments().size());
        assertEquals(0, bookQueue.getArguments().size());
        assertEquals("notification.dead.queue", notificationDeadQueue.getName());
        assertEquals("book.dead.queue", bookDeadQueue.getName());
    }
}
