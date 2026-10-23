package org.darkroomlibrary.config;

import com.rabbitmq.client.MalformedFrameException;
import com.rabbitmq.client.impl.ValueReader;
import com.rabbitmq.client.impl.ValueWriter;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.amqp.RabbitAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/** Bounded witnesses: no oversized allocation or stack exhaustion is attempted. */
class RabbitClientBoundaryTest {
    @BeforeAll
    static void bindsTheActuallyLoadedClientVersion() {
        String expected = System.getProperty("darkroom.expected-client-version", "5.37.0");
        String actual = com.rabbitmq.client.ConnectionFactory.class.getPackage().getImplementationVersion();
        assertEquals(expected, actual, "loaded RabbitMQ JAR must match the selected comparison version");
        System.out.println("RabbitMQ boundary client=" + actual);
    }

    @ParameterizedTest
    @ValueSource(chars = {'S', 'x'})
    void refusesDeclaredLengthBeforeReadingAbsentFieldBytes(char type) throws Exception {
        byte[] wire = lengthClaim(type);
        assertThrows(MalformedFrameException.class,
                () -> new ValueReader(new DataInputStream(new ByteArrayInputStream(wire))).readTable());
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void rejectsDeepTablesAndMixedArrays(boolean mixed) throws Exception {
        byte[] wire = table(nested(40, mixed));
        assertThrows(MalformedFrameException.class,
                () -> new ValueReader(new DataInputStream(new ByteArrayInputStream(wire))).readTable());
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void preservesOrdinaryNestedFields(boolean mixed) throws Exception {
        Map<String, Object> value = nested(4, mixed);
        assertEquals(value, new ValueReader(new DataInputStream(
                new ByteArrayInputStream(table(value)))).readTable());
    }

    @Test
    void springConnectionRejectsMalformedPreAuthenticationProperties() throws Exception {
        try (ServerSocket peer = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
            peer.setSoTimeout(5000);
            CompletableFuture<Void> server = CompletableFuture.runAsync(() -> {
                try (Socket socket = peer.accept()) {
                    socket.setSoTimeout(5000);
                    assertArrayEquals(new byte[]{65, 77, 81, 80, 0, 0, 9, 1},
                            socket.getInputStream().readNBytes(8));
                    ByteArrayOutputStream payload = new ByteArrayOutputStream();
                    DataOutputStream method = new DataOutputStream(payload);
                    method.writeShort(10); // connection
                    method.writeShort(10); // start
                    method.writeByte(0);
                    method.writeByte(9);
                    method.write(lengthClaim('S'));
                    method.writeInt(5);
                    method.writeBytes("PLAIN");
                    method.writeInt(5);
                    method.writeBytes("en_US");
                    DataOutputStream frame = new DataOutputStream(socket.getOutputStream());
                    frame.writeByte(1);
                    frame.writeShort(0);
                    frame.writeInt(payload.size());
                    frame.write(payload.toByteArray());
                    frame.writeByte(0xCE);
                    frame.flush();
                    while (socket.getInputStream().read() != -1) { /* wait for client close */ }
                } catch (Exception failure) {
                    throw new IllegalStateException(failure);
                }
            });
            new ApplicationContextRunner()
                    .withConfiguration(AutoConfigurations.of(RabbitAutoConfiguration.class))
                    .withPropertyValues("spring.rabbitmq.host=127.0.0.1",
                            "spring.rabbitmq.port=" + peer.getLocalPort(),
                            "spring.rabbitmq.username=boundary-fixture",
                            "spring.rabbitmq.password=boundary-fixture-only",
                            "spring.rabbitmq.connection-timeout=2000ms",
                            "spring.rabbitmq.listener.simple.auto-startup=false")
                    .run(context -> {
                        assertNull(context.getStartupFailure());
                        CachingConnectionFactory factory = context.getBean(CachingConnectionFactory.class);
                        Exception failure = assertThrows(Exception.class, factory::createConnection);
                        Throwable cause = failure;
                        while (cause != null && !(cause instanceof MalformedFrameException)) {
                            cause = cause.getCause();
                        }
                        Throwable root = failure;
                        while (root.getCause() != null) { root = root.getCause(); }
                        assertNotNull(cause, "Spring must retain the malformed wire-field cause; root=" + root);
                    });
            server.get(6, TimeUnit.SECONDS);
        }
    }

    private static byte[] lengthClaim(char type) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(bytes);
        out.writeInt(7); // one short key, type tag, and length, with no field data
        out.writeByte(1);
        out.writeByte('k');
        out.writeByte(type);
        out.writeInt(65536); // old client allocates only 64 KiB in this safe counterexample
        return bytes.toByteArray();
    }

    private static Map<String, Object> nested(int depth, boolean mixed) {
        Object value = 7;
        for (int i = 0; i < depth; i++) {
            value = mixed && i % 2 == 0 ? List.of(value) : Map.of("k", value);
        }
        return Map.of("root", value);
    }

    private static byte[] table(Map<String, Object> value) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        new ValueWriter(new DataOutputStream(bytes)).writeTable(value);
        return bytes.toByteArray();
    }
}
