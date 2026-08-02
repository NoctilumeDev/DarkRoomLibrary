package org.darkroomlibrary.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.config.RetryInterceptorBuilder;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.retry.MessageRecoverer;
import org.springframework.amqp.rabbit.retry.RepublishMessageRecoverer;
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

    @Value("${middleware.rabbit.notification-routing-key:notification.task}")
    private String notificationRoutingKey;

    @Value("${middleware.rabbit.book-returned-routing-key:book.returned}")
    private String bookReturnedRoutingKey;

    @Value("${middleware.rabbit.notification-task-queue:dark.room.library.notification-task}")
    private String notificationTaskQueueName;

    @Value("${middleware.rabbit.book-returned-queue:dark.room.library.book-returned}")
    private String bookReturnedQueueName;

    @Value("${middleware.rabbit.dead-letter-exchange:dark.room.library.dead-letter}")
    private String deadLetterExchangeName;

    @Value("${middleware.rabbit.notification-dead-letter-queue:dark.room.library.notification-task.dead}")
    private String notificationDeadLetterQueueName;

    @Value("${middleware.rabbit.book-returned-dead-letter-queue:dark.room.library.book-returned.dead}")
    private String bookReturnedDeadLetterQueueName;

    @Value("${middleware.rabbit.notification-dead-letter-routing-key:dead.notification.task}")
    private String notificationDeadLetterRoutingKey;

    @Value("${middleware.rabbit.book-returned-dead-letter-routing-key:dead.book.returned}")
    private String bookReturnedDeadLetterRoutingKey;

    @Bean
    public DirectExchange domainEventExchange() {
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
            MessageRecoverer poisonMessageRecoverer,
            @Value("${middleware.rabbit.recovery-interval-ms:30000}") long recoveryIntervalMs) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        configurer.configure(factory, connectionFactory);
        factory.setRecoveryInterval(recoveryIntervalMs);
        factory.setDefaultRequeueRejected(false);
        factory.setContainerCustomizer(container -> container.setAdviceChain(
                RetryInterceptorBuilder.stateless()
                        .maxAttempts(1)
                        .recoverer(poisonMessageRecoverer)
                        .build()));
        return factory;
    }

    @Bean
    public MessageRecoverer poisonMessageRecoverer(RabbitTemplate rabbitTemplate) {
        RepublishMessageRecoverer recoverer =
                new RepublishMessageRecoverer(rabbitTemplate, deadLetterExchangeName);
        recoverer.errorRoutingKeyPrefix("dead.");
        return recoverer;
    }

    @Bean
    public Queue notificationTaskQueue() {
        return QueueBuilder.durable(notificationTaskQueueName).build();
    }

    @Bean
    public Queue bookReturnedQueue() {
        return QueueBuilder.durable(bookReturnedQueueName).build();
    }

    @Bean
    public DirectExchange deadLetterExchange() {
        return new DirectExchange(deadLetterExchangeName, true, false);
    }

    @Bean
    public Queue notificationDeadLetterQueue() {
        return QueueBuilder.durable(notificationDeadLetterQueueName).build();
    }

    @Bean
    public Queue bookReturnedDeadLetterQueue() {
        return QueueBuilder.durable(bookReturnedDeadLetterQueueName).build();
    }

    @Bean
    public Binding notificationTaskBinding(Queue notificationTaskQueue, DirectExchange domainEventExchange) {
        return BindingBuilder.bind(notificationTaskQueue).to(domainEventExchange).with(notificationRoutingKey);
    }

    @Bean
    public Binding bookReturnedBinding(Queue bookReturnedQueue, DirectExchange domainEventExchange) {
        return BindingBuilder.bind(bookReturnedQueue).to(domainEventExchange).with(bookReturnedRoutingKey);
    }

    @Bean
    public Binding notificationDeadLetterBinding(
            Queue notificationDeadLetterQueue,
            DirectExchange deadLetterExchange) {
        return BindingBuilder.bind(notificationDeadLetterQueue)
                .to(deadLetterExchange)
                .with(notificationDeadLetterRoutingKey);
    }

    @Bean
    public Binding bookReturnedDeadLetterBinding(
            Queue bookReturnedDeadLetterQueue,
            DirectExchange deadLetterExchange) {
        return BindingBuilder.bind(bookReturnedDeadLetterQueue)
                .to(deadLetterExchange)
                .with(bookReturnedDeadLetterRoutingKey);
    }
}
