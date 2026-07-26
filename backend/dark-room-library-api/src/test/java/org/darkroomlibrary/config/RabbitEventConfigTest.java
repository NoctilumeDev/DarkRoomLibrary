package org.darkroomlibrary.config;

import org.darkroomlibrary.domain.model.OperationLog;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.support.converter.MessageConverter;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

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
}
