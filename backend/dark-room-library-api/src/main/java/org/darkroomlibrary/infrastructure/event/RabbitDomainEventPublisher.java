package org.darkroomlibrary.infrastructure.event;

import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import jakarta.annotation.Resource;

@Slf4j
@Component
public class RabbitDomainEventPublisher implements DomainEventPublisher {

    @Value("${middleware.rabbit.enabled:false}")
    private boolean enabled;

    @Value("${middleware.rabbit.exchange:dark.room.library.events}")
    private String exchange;

    @Resource
    private RabbitTemplate rabbitTemplate;

    @Override
    public boolean publish(String routingKey, Object payload) {
        if (!enabled) {
            return false;
        }
        try {
            rabbitTemplate.convertAndSend(exchange, routingKey, payload);
            return true;
        } catch (Exception e) {
            log.warn("RabbitMQ事件发送失败，已触发降级: routingKey={}, error={}", routingKey, e.getMessage());
            return false;
        }
    }
}
