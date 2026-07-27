package org.darkroomlibrary.service;

import org.springframework.amqp.rabbit.connection.Connection;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Checks core readiness separately from optional middleware availability.
 */
@Service
public class SystemHealthService {

    private static final String UP = "UP";
    private static final String DOWN = "DOWN";
    private static final String DISABLED = "DISABLED";

    private final DataSource dataSource;
    private final RedisConnectionFactory redisConnectionFactory;
    private final ConnectionFactory rabbitConnectionFactory;
    private final Path uploadRoot;
    private final boolean redisEnabled;
    private final boolean rabbitEnabled;

    public SystemHealthService(
            DataSource dataSource,
            RedisConnectionFactory redisConnectionFactory,
            ConnectionFactory rabbitConnectionFactory,
            @Value("${file.upload-dir:./upload/pic}") String uploadDir,
            @Value("${middleware.redis.enabled:false}") boolean redisEnabled,
            @Value("${middleware.rabbit.enabled:false}") boolean rabbitEnabled) {
        this.dataSource = dataSource;
        this.redisConnectionFactory = redisConnectionFactory;
        this.rabbitConnectionFactory = rabbitConnectionFactory;
        this.uploadRoot = Path.of(uploadDir).toAbsolutePath().normalize();
        this.redisEnabled = redisEnabled;
        this.rabbitEnabled = rabbitEnabled;
    }

    public HealthReport checkReadiness() {
        Map<String, String> components = new LinkedHashMap<>();
        components.put("database", checkDatabase());
        components.put("fileStorage", checkFileStorage());
        components.put("redis", redisEnabled ? checkRedis() : DISABLED);
        components.put("rabbitMq", rabbitEnabled ? checkRabbitMq() : DISABLED);

        boolean coreReady = UP.equals(components.get("database"))
                && UP.equals(components.get("fileStorage"));
        boolean optionalReady = (!redisEnabled || UP.equals(components.get("redis")))
                && (!rabbitEnabled || UP.equals(components.get("rabbitMq")));
        String status = coreReady ? (optionalReady ? UP : "DEGRADED") : DOWN;
        return new HealthReport(status, Instant.now(), Map.copyOf(components));
    }

    private String checkDatabase() {
        try (java.sql.Connection connection = dataSource.getConnection()) {
            return connection.isValid(2) ? UP : DOWN;
        } catch (SQLException exception) {
            return DOWN;
        }
    }

    private String checkFileStorage() {
        try {
            Files.createDirectories(uploadRoot);
            return Files.isDirectory(uploadRoot) && Files.isWritable(uploadRoot) ? UP : DOWN;
        } catch (Exception exception) {
            return DOWN;
        }
    }

    private String checkRedis() {
        try (RedisConnection connection = redisConnectionFactory.getConnection()) {
            return "PONG".equalsIgnoreCase(connection.ping()) ? UP : DOWN;
        } catch (Exception exception) {
            return DOWN;
        }
    }

    private String checkRabbitMq() {
        try (Connection connection = rabbitConnectionFactory.createConnection()) {
            return connection.isOpen() ? UP : DOWN;
        } catch (Exception exception) {
            return DOWN;
        }
    }

    public record HealthReport(
            String status,
            Instant checkedAt,
            Map<String, String> components) {

        public boolean acceptsTraffic() {
            return !DOWN.equals(status);
        }
    }
}
