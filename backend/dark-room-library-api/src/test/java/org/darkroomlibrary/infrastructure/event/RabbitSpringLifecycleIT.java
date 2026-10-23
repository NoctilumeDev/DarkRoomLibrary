package org.darkroomlibrary.infrastructure.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.darkroomlibrary.config.RabbitEventConfig;
import org.darkroomlibrary.service.NotificationService;
import org.darkroomlibrary.service.ReservationWorkflowService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeAll;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.annotation.EnableRabbit;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.listener.RabbitListenerEndpointRegistry;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.amqp.RabbitAutoConfiguration;
import org.springframework.boot.test.context.assertj.AssertableApplicationContext;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

import java.net.InetAddress;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Explicit live test: requires a disposable, test-only vhost; never uses the default vhost. */
class RabbitSpringLifecycleIT {
    @BeforeAll
    static void bindsTheActuallyLoadedClientVersion() {
        String expected = System.getProperty("darkroom.expected-client-version", "5.37.0");
        String actual = com.rabbitmq.client.ConnectionFactory.class.getPackage().getImplementationVersion();
        assertEquals(expected, actual, "loaded RabbitMQ JAR must match the selected comparison version");
        System.out.println("RabbitMQ lifecycle client=" + actual);
    }

    @Configuration(proxyBeanMethods = false)
    @EnableRabbit
    @Import({RabbitEventConfig.class, RabbitDomainEventPublisher.class,
            NotificationTaskConsumer.class, BookReturnedConsumer.class})
    static class EventApplication {
        @Bean ObjectMapper objectMapper() { return new ObjectMapper().findAndRegisterModules(); }
        @Bean NotificationService notificationService() { return mock(NotificationService.class); }
        @Bean ReservationWorkflowService reservationWorkflowService() {
            return mock(ReservationWorkflowService.class);
        }
    }

    @Test
    void confirmsAndConvertsBothIntegerEventsThenAcknowledges() {
        withBroker((context, name) -> {
            RabbitDomainEventPublisher publisher = context.getBean(RabbitDomainEventPublisher.class);
            assertTrue(publisher.publish("notification.task", 17));
            assertTrue(publisher.publish("book.returned", 23));
            verify(context.getBean(NotificationService.class), timeout(5000).times(1)).processTask(17);
            verify(context.getBean(ReservationWorkflowService.class), timeout(5000).times(1)).onBookReturned(23);
            // Graceful consumer stop flushes ACKs; an unacknowledged delivery would become ready again.
            context.getBean(RabbitListenerEndpointRegistry.class).stop();
            assertNull(context.getBean(RabbitTemplate.class).receive(name + ".notification"));
            assertNull(context.getBean(RabbitTemplate.class).receive(name + ".book"));
        });
    }

    @Test
    void malformedJsonUsesApplicationRepublishRecovererNotBrokerDlx() {
        withBroker((context, name) -> {
            RabbitTemplate template = context.getBean(RabbitTemplate.class);
            byte[] original = "not-json".getBytes(StandardCharsets.UTF_8);
            MessageProperties properties = new MessageProperties();
            properties.setContentType(MessageProperties.CONTENT_TYPE_JSON);
            properties.setHeader("__TypeId__", "java.lang.Integer");
            template.send(name + ".events", "notification.task", new Message(original, properties));
            Message failed = template.receive(name + ".notification.dead", 5000);
            assertNotNull(failed, "application recovery must republish conversion failure");
            assertArrayEquals(original, failed.getBody());
            assertEquals("notification.task", failed.getMessageProperties().getHeader("x-original-routingKey"));
            assertNotNull(failed.getMessageProperties().getHeader("x-exception-message"));
            verifyNoInteractions(context.getBean(NotificationService.class));
            context.getBean(RabbitListenerEndpointRegistry.class).stop();
            assertNull(template.receive(name + ".notification"));
            assertNull(template.receive(name + ".notification.dead"));
        });
    }

    @Test
    void handlerFailureIsRepublishedOnceWithoutRequeueLoop() {
        withBroker((context, name) -> {
            NotificationService service = context.getBean(NotificationService.class);
            doThrow(new IllegalStateException("synthetic-handler-failure")).when(service).processTask(29);
            assertTrue(context.getBean(RabbitDomainEventPublisher.class).publish("notification.task", 29));
            RabbitTemplate template = context.getBean(RabbitTemplate.class);
            Message failed = template.receive(name + ".notification.dead", 5000);
            assertNotNull(failed);
            assertEquals("29", new String(failed.getBody(), StandardCharsets.UTF_8));
            assertNotNull(failed.getMessageProperties().getHeader("x-exception-stacktrace"));
            context.getBean(RabbitListenerEndpointRegistry.class).stop();
            verify(service, times(1)).processTask(29);
            assertNull(template.receive(name + ".notification"));
            assertNull(template.receive(name + ".notification.dead"));
        });
    }

    @Test
    void mandatoryReturnIsNotReportedAsSuccessAndCooldownRecovers() {
        withBroker((context, name) -> {
            RabbitDomainEventPublisher publisher = context.getBean(RabbitDomainEventPublisher.class);
            assertFalse(publisher.publish("unbound.route", 31));
            assertFalse(publisher.publish("notification.task", 32)); // inside the cooldown
            pause(300);
            assertTrue(publisher.publish("notification.task", 33));
            NotificationService service = context.getBean(NotificationService.class);
            verify(service, timeout(5000)).processTask(33);
            verify(service, never()).processTask(32);
        });
    }

    @Test
    void listenersReconnectAfterClientConnectionReset() {
        withBroker((context, name) -> {
            RabbitDomainEventPublisher publisher = context.getBean(RabbitDomainEventPublisher.class);
            assertTrue(publisher.publish("book.returned", 41));
            ReservationWorkflowService service = context.getBean(ReservationWorkflowService.class);
            verify(service, timeout(5000)).onBookReturned(41);
            context.getBean(CachingConnectionFactory.class).resetConnection();
            await(() -> context.getBean(RabbitAdmin.class).getQueueInfo(name + ".book").getConsumerCount() == 1);
            assertTrue(publisher.publish("book.returned", 42));
            verify(service, timeout(5000)).onBookReturned(42);
        });
    }

    @Test
    void disabledModeDoesNotStartListenersOrOpenAConnection() {
        runner("disabled", false).run(context -> {
            assertNull(context.getStartupFailure());
            assertTrue(context.getBean(RabbitListenerEndpointRegistry.class).getListenerContainers().isEmpty());
            CachingConnectionFactory factory = context.getBean(CachingConnectionFactory.class);
            assertEquals(CachingConnectionFactory.CacheMode.CHANNEL, factory.getCacheMode());
            // CHANNEL cache reports port 0 when its connection target has never been created.
            assertEquals("0", factory.getCacheProperties().getProperty("localPort"));
            var connectionListener = mock(org.springframework.amqp.rabbit.connection.ConnectionListener.class);
            factory.addConnectionListener(connectionListener);
            assertFalse(context.getBean(RabbitDomainEventPublisher.class).publish("notification.task", 51));
            verifyNoInteractions(connectionListener);
            assertEquals("0", factory.getCacheProperties().getProperty("localPort"));
            verifyNoInteractions(context.getBean(NotificationService.class));
        });
    }

    @Test
    void unavailablePeerReturnsFallbackAndKeepsCooldown() throws Exception {
        // An ephemeral loopback endpoint refuses connections. Never stop or reconfigure the shared broker.
        int port;
        try (ServerSocket reservation = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
            port = reservation.getLocalPort();
        }
        runner("unavailable", true)
                .withPropertyValues("spring.rabbitmq.port=" + port,
                        "spring.rabbitmq.listener.simple.auto-startup=false",
                        "spring.rabbitmq.dynamic=false")
                .run(context -> {
                    assertNull(context.getStartupFailure());
                    RabbitDomainEventPublisher publisher = context.getBean(RabbitDomainEventPublisher.class);
                    var connectionListener = mock(org.springframework.amqp.rabbit.connection.ConnectionListener.class);
                    context.getBean(CachingConnectionFactory.class).addConnectionListener(connectionListener);
                    assertFalse(publisher.publish("notification.task", 61));
                    assertFalse(publisher.publish("notification.task", 62));
                    verify(connectionListener, times(1)).onFailed(any(Exception.class));
                    verify(connectionListener, never()).onCreate(any());
                    verifyNoInteractions(context.getBean(NotificationService.class));
                });
    }

    private static ApplicationContextRunner runner(String name, boolean enabled) {
        assertEquals("1", System.getenv("DARKROOM_MQ_TEST"), "explicit live-test opt-in required");
        String vhost = System.getenv("DARKROOM_MQ_TEST_VHOST");
        assertNotNull(vhost);
        assertTrue(vhost.startsWith("darkroom-test-"), "dedicated test vhost required");
        String user = System.getenv("DARKROOM_MQ_TEST_USER");
        String password = System.getenv("DARKROOM_MQ_TEST_PASSWORD");
        assertNotNull(user);
        assertNotNull(password);
        return new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(RabbitAutoConfiguration.class))
                .withUserConfiguration(EventApplication.class)
                .withPropertyValues("spring.rabbitmq.host=127.0.0.1",
                        "spring.rabbitmq.port=" + System.getenv().getOrDefault("DARKROOM_MQ_TEST_PORT", "5672"),
                        "spring.rabbitmq.virtual-host=" + vhost,
                        "spring.rabbitmq.username=" + user,
                        "spring.rabbitmq.password=" + password,
                        "spring.rabbitmq.connection-timeout=500ms",
                        "spring.rabbitmq.publisher-confirm-type=correlated",
                        "spring.rabbitmq.publisher-returns=true",
                        "spring.rabbitmq.template.mandatory=true",
                        "spring.rabbitmq.listener.simple.auto-startup=" + enabled,
                        "middleware.rabbit.enabled=" + enabled,
                        "middleware.rabbit.confirm-timeout-ms=2000",
                        "middleware.rabbit.recovery-interval-ms=200",
                        "middleware.rabbit.exchange=" + name + ".events",
                        "middleware.rabbit.notification-task-queue=" + name + ".notification",
                        "middleware.rabbit.book-returned-queue=" + name + ".book",
                        "middleware.rabbit.dead-letter-exchange=" + name + ".dead",
                        "middleware.rabbit.notification-dead-letter-queue=" + name + ".notification.dead",
                        "middleware.rabbit.book-returned-dead-letter-queue=" + name + ".book.dead");
    }

    @FunctionalInterface
    private interface Scenario { void run(AssertableApplicationContext context, String name); }

    private static void withBroker(Scenario scenario) {
        String name = "darkroom-test-" + UUID.randomUUID();
        runner(name, true).run(context -> {
            assertNull(context.getStartupFailure());
            RabbitAdmin admin = context.getBean(RabbitAdmin.class);
            try {
                await(() -> admin.getQueueInfo(name + ".notification") != null
                        && admin.getQueueInfo(name + ".notification").getConsumerCount() == 1
                        && admin.getQueueInfo(name + ".book").getConsumerCount() == 1);
                assertTrue(context.getBean(RabbitEventConfig.class).notificationTaskQueue().getArguments().isEmpty());
                scenario.run(context, name);
            } finally {
                context.getBean(RabbitListenerEndpointRegistry.class).stop();
                for (String suffix : List.of(".notification", ".book", ".notification.dead", ".book.dead")) {
                    admin.deleteQueue(name + suffix);
                }
                admin.deleteExchange(name + ".events");
                admin.deleteExchange(name + ".dead");
            }
        });
    }

    private static void await(BooleanSupplier condition) {
        long deadline = System.nanoTime() + Duration.ofSeconds(8).toNanos();
        while (System.nanoTime() < deadline) {
            if (condition.getAsBoolean()) { return; }
            pause(25);
        }
        fail("RabbitMQ lifecycle condition not observed before timeout");
    }

    private static void pause(long milliseconds) {
        try { Thread.sleep(milliseconds); }
        catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(interrupted);
        }
    }
}
