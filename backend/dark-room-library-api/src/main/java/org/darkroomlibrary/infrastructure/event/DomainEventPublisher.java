package org.darkroomlibrary.infrastructure.event;

public interface DomainEventPublisher {

    boolean publish(String routingKey, Object payload);
}
