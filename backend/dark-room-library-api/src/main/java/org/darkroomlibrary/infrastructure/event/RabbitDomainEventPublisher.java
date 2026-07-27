package org.darkroomlibrary.infrastructure.event;

import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.ReturnedMessage;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import jakarta.annotation.Resource;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

@Slf4j
@Component
public class RabbitDomainEventPublisher implements DomainEventPublisher {

    @Value("${middleware.rabbit.enabled:false}")
    private boolean enabled;

    @Value("${middleware.rabbit.exchange:dark.room.library.events}")
    private String exchange;

    @Value("${middleware.rabbit.confirm-timeout-ms:1000}")
    private long confirmTimeoutMs;

    @Value("${middleware.rabbit.recovery-interval-ms:30000}")
    private long recoveryIntervalMs;

    @Resource
    private RabbitTemplate rabbitTemplate;

    private final AtomicLong unavailableUntil = new AtomicLong(0);

    @Override
    public boolean publish(String routingKey, Object payload) {
        if (!enabled || System.currentTimeMillis() < unavailableUntil.get()) {
            return false;
        }
        try {
            CorrelationData correlationData = new CorrelationData(UUID.randomUUID().toString());
            rabbitTemplate.convertAndSend(exchange, routingKey, payload, correlationData);
            CorrelationData.Confirm confirm = correlationData.getFuture().get(
                    Math.max(1L, confirmTimeoutMs), TimeUnit.MILLISECONDS);
            ReturnedMessage returned = correlationData.getReturned();
            if (returned != null) {
                log.warn("RabbitMQ事件无法路由，已触发降级: routingKey={}, replyText={}",
                        routingKey, returned.getReplyText());
                markUnavailable();
                return false;
            }
            if (confirm == null || !confirm.isAck()) {
                log.warn("RabbitMQ事件未获broker确认，已触发降级: routingKey={}, reason={}",
                        routingKey, confirm == null ? null : confirm.getReason());
                markUnavailable();
                return false;
            }
            unavailableUntil.set(0);
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("RabbitMQ事件确认等待被中断，已触发降级: routingKey={}", routingKey);
            markUnavailable();
            return false;
        } catch (Exception e) {
            log.warn("RabbitMQ事件发送失败，已触发降级: routingKey={}, error={}", routingKey, e.getMessage());
            markUnavailable();
            return false;
        }
    }

    private void markUnavailable() {
        unavailableUntil.set(System.currentTimeMillis() + Math.max(1L, recoveryIntervalMs));
    }
}
