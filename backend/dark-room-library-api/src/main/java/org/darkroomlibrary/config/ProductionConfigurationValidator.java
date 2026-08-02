package org.darkroomlibrary.config;

import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Set;

/**
 * Rejects known development credentials when the production profile is active.
 */
@Component
@Profile("prod")
public class ProductionConfigurationValidator implements InitializingBean {

    private static final Set<String> INSECURE_DATABASE_PASSWORDS = Set.of(
            "root",
            "password",
            "change-me",
            "replace-me",
            "darkroommysql@20606",
            "replace-with-a-strong-local-password"
    );
    private static final Set<String> INSECURE_JWT_SECRETS = Set.of(
            "dark-room-library-dev-jwt-secret-key-please-change-in-production",
            "dark-room-library-compose-jwt-secret-change-before-public-deployment",
            "at-least-32-random-bytes",
            "replace-with-at-least-32-random-bytes",
            "test-secret-key-for-unit-tests-only"
    );
    private static final Set<String> INSECURE_RABBIT_PASSWORDS = Set.of(
            "guest",
            "darkroomrabbit@20606",
            "replace-with-a-strong-local-password"
    );

    private final String databasePassword;
    private final String jwtSecret;
    private final boolean rabbitEnabled;
    private final String rabbitPassword;
    private final String notificationAlertWebhookUrl;

    public ProductionConfigurationValidator(
            @Value("${spring.datasource.password}") String databasePassword,
            @Value("${jwt.secret}") String jwtSecret,
            @Value("${middleware.rabbit.enabled:false}") boolean rabbitEnabled,
            @Value("${spring.rabbitmq.password:}") String rabbitPassword,
            @Value("${notification.alert.webhook-url:}") String notificationAlertWebhookUrl) {
        this.databasePassword = databasePassword;
        this.jwtSecret = jwtSecret;
        this.rabbitEnabled = rabbitEnabled;
        this.rabbitPassword = rabbitPassword;
        this.notificationAlertWebhookUrl = notificationAlertWebhookUrl;
    }

    @Override
    public void afterPropertiesSet() {
        rejectKnownValue("数据库密码", databasePassword, INSECURE_DATABASE_PASSWORDS);
        rejectKnownValue("JWT 密钥", jwtSecret, INSECURE_JWT_SECRETS);
        if (jwtSecret == null || jwtSecret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException("生产环境 JWT 密钥必须至少包含 32 个 UTF-8 字节");
        }
        if (rabbitEnabled) {
            rejectKnownValue("RabbitMQ 密码", rabbitPassword, INSECURE_RABBIT_PASSWORDS);
            if (notificationAlertWebhookUrl == null || notificationAlertWebhookUrl.isBlank()) {
                throw new IllegalStateException(
                        "生产环境启用 RabbitMQ 时必须配置通知与死信告警 Webhook");
            }
        }
    }

    private void rejectKnownValue(String name, String value, Set<String> insecureValues) {
        String normalized = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
        if (normalized.isEmpty() || insecureValues.contains(normalized)) {
            throw new IllegalStateException("生产环境不能使用空值或已知默认" + name);
        }
    }
}
