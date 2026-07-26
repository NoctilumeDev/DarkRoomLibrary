package org.darkroomlibrary.infrastructure.event;

import org.darkroomlibrary.mapper.OperationLogMapper;
import org.darkroomlibrary.domain.model.OperationLog;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import jakarta.annotation.Resource;

@Component
@ConditionalOnProperty(name = "middleware.rabbit.enabled", havingValue = "true")
public class OperationLogConsumer {

    @Resource
    private OperationLogMapper operationLogMapper;

    @RabbitListener(queues = "book.manage.operation-log")
    public void consume(OperationLog operationLog) {
        operationLogMapper.insert(operationLog);
    }
}
