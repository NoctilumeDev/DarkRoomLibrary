package org.darkroomlibrary.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.support.converter.DefaultJackson2JavaTypeMapper;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.amqp.SimpleRabbitListenerContainerFactoryConfigurer;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty(name = "middleware.rabbit.enabled", havingValue = "true")
public class RabbitEventConfig {

    @Value("${middleware.rabbit.exchange:dark.room.library.events}")
    private String exchangeName;

    @Value("${middleware.rabbit.operation-log-routing-key:operation.log}")
    private String operationLogRoutingKey;

    @Value("${middleware.rabbit.notification-routing-key:notification.task}")
    private String notificationRoutingKey;

    @Value("${middleware.rabbit.book-returned-routing-key:book.returned}")
    private String bookReturnedRoutingKey;

    @Bean
    public DirectExchange bookManageExchange() {
        return new DirectExchange(exchangeName, true, false);
    }

    @Bean
    public MessageConverter messageConverter(ObjectMapper objectMapper) {
        Jackson2JsonMessageConverter converter = new Jackson2JsonMessageConverter(objectMapper);
        DefaultJackson2JavaTypeMapper typeMapper = new DefaultJackson2JavaTypeMapper();
        typeMapper.setTrustedPackages("org.darkroomlibrary.domain.model");
        converter.setJavaTypeMapper(typeMapper);
        return converter;
    }

    @Bean(name = "rabbitListenerContainerFactory")
    public SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(
            SimpleRabbitListenerContainerFactoryConfigurer configurer,
            ConnectionFactory connectionFactory,
            @Value("${middleware.rabbit.recovery-interval-ms:30000}") long recoveryIntervalMs) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        configurer.configure(factory, connectionFactory);
        factory.setRecoveryInterval(recoveryIntervalMs);
        return factory;
    }

    @Bean
    public Queue operationLogQueue() {
        return new Queue("book.manage.operation-log", true);
    }

    @Bean
    public Queue notificationTaskQueue() {
        return new Queue("book.manage.notification-task", true);
    }

    @Bean
    public Queue bookReturnedQueue() {
        return new Queue("book.manage.book-returned", true);
    }

    @Bean
    public Binding operationLogBinding(Queue operationLogQueue, DirectExchange bookManageExchange) {
        return BindingBuilder.bind(operationLogQueue).to(bookManageExchange).with(operationLogRoutingKey);
    }

    @Bean
    public Binding notificationTaskBinding(Queue notificationTaskQueue, DirectExchange bookManageExchange) {
        return BindingBuilder.bind(notificationTaskQueue).to(bookManageExchange).with(notificationRoutingKey);
    }

    @Bean
    public Binding bookReturnedBinding(Queue bookReturnedQueue, DirectExchange bookManageExchange) {
        return BindingBuilder.bind(bookReturnedQueue).to(bookManageExchange).with(bookReturnedRoutingKey);
    }
}
