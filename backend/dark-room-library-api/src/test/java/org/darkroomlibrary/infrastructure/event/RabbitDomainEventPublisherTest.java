package org.darkroomlibrary.infrastructure.event;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class RabbitDomainEventPublisherTest {

    @Mock
    private RabbitTemplate rabbitTemplate;

    private RabbitDomainEventPublisher publisher;

    @BeforeEach
    void setUp() {
        publisher = new RabbitDomainEventPublisher();
        ReflectionTestUtils.setField(publisher, "enabled", true);
        ReflectionTestUtils.setField(publisher, "exchange", "test.exchange");
        ReflectionTestUtils.setField(publisher, "confirmTimeoutMs", 100L);
        ReflectionTestUtils.setField(publisher, "recoveryIntervalMs", 60_000L);
        ReflectionTestUtils.setField(publisher, "rabbitTemplate", rabbitTemplate);
    }

    @Test
    void returnsTrueOnlyAfterBrokerAck() {
        completeConfirm(true, null);

        assertTrue(publisher.publish("test.route", 42));
    }

    @Test
    void fallsBackWhenBrokerNacks() {
        completeConfirm(false, "rejected");

        assertFalse(publisher.publish("test.route", 42));
    }

    @Test
    void skipsRepeatedBrokerCallsDuringRecoveryWindow() {
        completeConfirm(false, "rejected");

        assertFalse(publisher.publish("test.route", 42));
        assertFalse(publisher.publish("test.route", 43));

        verify(rabbitTemplate, times(1)).convertAndSend(
                eq("test.exchange"),
                eq("test.route"),
                any(Integer.class),
                any(CorrelationData.class));
    }

    private void completeConfirm(boolean ack, String reason) {
        doAnswer(invocation -> {
            CorrelationData correlationData = invocation.getArgument(3);
            correlationData.getFuture().complete(new CorrelationData.Confirm(ack, reason));
            return null;
        }).when(rabbitTemplate).convertAndSend(
                eq("test.exchange"),
                eq("test.route"),
                any(Integer.class),
                any(CorrelationData.class));
    }
}
