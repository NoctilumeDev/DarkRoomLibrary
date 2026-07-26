package org.darkroomlibrary.infrastructure.event;

import org.darkroomlibrary.service.ReservationWorkflowService;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import jakarta.annotation.Resource;

@Component
@ConditionalOnProperty(name = "middleware.rabbit.enabled", havingValue = "true")
public class BookReturnedConsumer {

    @Resource
    private ReservationWorkflowService reservationWorkflowService;

    @RabbitListener(queues = "book.manage.book-returned")
    public void consume(Integer bookId) {
        reservationWorkflowService.onBookReturned(bookId);
    }
}
