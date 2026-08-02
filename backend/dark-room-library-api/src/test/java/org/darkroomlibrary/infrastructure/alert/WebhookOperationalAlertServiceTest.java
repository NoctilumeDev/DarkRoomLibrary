package org.darkroomlibrary.infrastructure.alert;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.darkroomlibrary.domain.model.NotificationTask;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WebhookOperationalAlertServiceTest {

    @Test
    @SuppressWarnings("unchecked")
    void sendsConfiguredWebhookWithoutExposingCredentialsInHeaders() {
        HttpClient client = mock(HttpClient.class);
        HttpResponse<Void> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(204);
        when(client.sendAsync(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(CompletableFuture.completedFuture(response));
        WebhookOperationalAlertService service = new WebhookOperationalAlertService(
                new ObjectMapper(),
                client,
                "https://alerts.example.test/dark-room-library",
                "secret-token",
                1000);

        service.notificationTaskDead(task(), 8, "mail unavailable");

        ArgumentCaptor<HttpRequest> requestCaptor = ArgumentCaptor.forClass(HttpRequest.class);
        verify(client).sendAsync(requestCaptor.capture(), any(HttpResponse.BodyHandler.class));
        HttpRequest request = requestCaptor.getValue();
        assertEquals("https://alerts.example.test/dark-room-library", request.uri().toString());
        assertEquals("Bearer secret-token",
                request.headers().firstValue("Authorization").orElseThrow());
    }

    @Test
    void staysDisabledWhenWebhookUrlIsBlank() {
        HttpClient client = mock(HttpClient.class);
        WebhookOperationalAlertService service = new WebhookOperationalAlertService(
                new ObjectMapper(), client, "", "", 1000);

        service.notificationTaskDead(task(), 8, "mail unavailable");

        verify(client, never()).sendAsync(any(), any());
    }

    @Test
    @SuppressWarnings("unchecked")
    void sendsDeadLetterBacklogAlert() {
        HttpClient client = mock(HttpClient.class);
        HttpResponse<Void> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(202);
        when(client.sendAsync(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(CompletableFuture.completedFuture(response));
        WebhookOperationalAlertService service = new WebhookOperationalAlertService(
                new ObjectMapper(),
                client,
                "https://alerts.example.test/dark-room-library",
                "",
                1000);

        service.deadLetterQueueBacklog("dark.room.library.notification-task.dead", 3);

        verify(client).sendAsync(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));
    }

    @Test
    void rejectsNonHttpWebhookSchemes() {
        assertThrows(IllegalArgumentException.class, () -> new WebhookOperationalAlertService(
                new ObjectMapper(),
                mock(HttpClient.class),
                "file:///tmp/alerts",
                "",
                1000));
    }

    @Test
    void rejectsWebhookWithoutHostOrWithEmbeddedCredentials() {
        HttpClient client = mock(HttpClient.class);

        assertThrows(IllegalArgumentException.class, () -> new WebhookOperationalAlertService(
                new ObjectMapper(), client, "https:///alerts", "", 1000));
        assertThrows(IllegalArgumentException.class, () -> new WebhookOperationalAlertService(
                new ObjectMapper(), client, "https://user:secret@alerts.example.test/hook", "", 1000));
    }

    private NotificationTask task() {
        return NotificationTask.builder()
                .id(17)
                .receiverEmail("reader@example.com")
                .subject("预约到书")
                .build();
    }
}
