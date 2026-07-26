package org.darkroomlibrary.infrastructure.event;

import org.darkroomlibrary.service.NotificationService;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import jakarta.annotation.Resource;

@Component
@ConditionalOnProperty(name = "middleware.rabbit.enabled", havingValue = "true")
public class NotificationTaskConsumer {

    @Resource
    private NotificationService notificationService;

    @RabbitListener(queues = "book.manage.notification-task")
    public void consume(Integer taskId) {
        notificationService.processTask(taskId);
    }
}
