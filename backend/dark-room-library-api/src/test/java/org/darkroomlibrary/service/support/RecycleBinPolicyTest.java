package org.darkroomlibrary.service.support;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RecycleBinPolicyTest {

    @Test
    void usesInjectedApplicationClockForNowAndDeadline() {
        Clock clock = Clock.fixed(
                Instant.parse("2026-10-01T20:18:00Z"),
                ZoneId.of("Asia/Shanghai"));
        RecycleBinPolicy policy = new RecycleBinPolicy(30, clock);

        LocalDateTime deletedAt = LocalDateTime.of(2026, 10, 2, 4, 18);
        assertEquals(deletedAt, policy.now());
        assertEquals(LocalDateTime.of(2026, 11, 1, 4, 18),
                policy.restoreDeadline(deletedAt));
    }

    @Test
    void rejectsUnboundedRetentionWindows() {
        Clock clock = Clock.systemUTC();
        assertThrows(IllegalArgumentException.class, () -> new RecycleBinPolicy(0, clock));
        assertThrows(IllegalArgumentException.class, () -> new RecycleBinPolicy(366, clock));
    }
}
