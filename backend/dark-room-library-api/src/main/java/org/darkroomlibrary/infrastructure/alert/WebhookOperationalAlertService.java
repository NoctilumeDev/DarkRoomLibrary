package org.darkroomlibrary.infrastructure.alert;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.darkroomlibrary.domain.model.NotificationTask;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

@Slf4j
@Service
public class WebhookOperationalAlertService implements OperationalAlertService {

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final URI webhookUri;
    private final String webhookToken;
    private final Duration requestTimeout;

    public WebhookOperationalAlertService(
            ObjectMapper objectMapper,
            HttpClient operationalAlertHttpClient,
            @Value("${notification.alert.webhook-url:}") String webhookUrl,
            @Value("${notification.alert.webhook-token:}") String webhookToken,
            @Value("${notification.alert.timeout-ms:3000}") long timeoutMs) {
        this.objectMapper = objectMapper;
        this.httpClient = operationalAlertHttpClient;
        this.webhookUri = parseWebhookUri(webhookUrl);
        this.webhookToken = webhookToken == null ? "" : webhookToken.trim();
        this.requestTimeout = Duration.ofMillis(Math.max(100L, timeoutMs));
    }

    @Override
    public void notificationTaskDead(NotificationTask task, int retryCount, String error) {
        if (task == null) {
            return;
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("taskId", task.getId());
        payload.put("retryCount", retryCount);
        payload.put("subject", task.getSubject());
        payload.put("receiver", maskEmail(task.getReceiverEmail()));
        payload.put("error", limit(error, 500));
        send("notification_task_dead", "taskId=" + task.getId(), payload);
    }

    @Override
    public void deadLetterQueueBacklog(String queueName, int messageCount) {
        if (queueName == null || queueName.isBlank()) {
            return;
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("queue", queueName);
        payload.put("messageCount", messageCount);
        send("rabbit_dead_letter_backlog", "queue=" + queueName, payload);
    }

    private void send(String event, String subject, Map<String, Object> payload) {
        if (webhookUri == null) {
            return;
        }
        try {
            payload.put("event", event);
            payload.put("occurredAt", OffsetDateTime.now().toString());
            HttpRequest.Builder requestBuilder = HttpRequest.newBuilder(webhookUri)
                    .timeout(requestTimeout)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(payload)));
            if (!webhookToken.isEmpty()) {
                requestBuilder.header("Authorization", "Bearer " + webhookToken);
            }
            httpClient.sendAsync(requestBuilder.build(), HttpResponse.BodyHandlers.discarding())
                    .whenComplete((response, throwable) -> {
                        if (throwable != null) {
                            log.warn("运维告警 Webhook 发送失败: event={}, {}, error={}",
                                    event, subject, throwable.getMessage());
                        } else if (response.statusCode() < 200 || response.statusCode() >= 300) {
                            log.warn("运维告警 Webhook 返回非成功状态: event={}, {}, status={}",
                                    event, subject, response.statusCode());
                        }
                    });
        } catch (Exception e) {
            log.warn("运维告警准备失败: event={}, {}, error={}", event, subject, e.getMessage());
        }
    }

    private static URI parseWebhookUri(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        URI uri = URI.create(value.trim());
        String scheme = uri.getScheme();
        if (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme)) {
            throw new IllegalArgumentException("通知告警 Webhook 只允许 http 或 https 地址");
        }
        if (uri.getHost() == null || uri.getHost().isBlank()) {
            throw new IllegalArgumentException("通知告警 Webhook 必须包含有效主机");
        }
        if (uri.getUserInfo() != null || uri.getFragment() != null) {
            throw new IllegalArgumentException("通知告警 Webhook 不允许内嵌凭据或片段");
        }
        return uri;
    }

    private static String maskEmail(String email) {
        if (email == null || email.isBlank()) {
            return "";
        }
        int at = email.indexOf('@');
        if (at <= 0 || at == email.length() - 1) {
            return "***";
        }
        return email.charAt(0) + "***@" + email.substring(at + 1);
    }

    private static String limit(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }
}
