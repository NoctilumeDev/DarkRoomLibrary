package org.darkroomlibrary.service.support;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDateTime;

/**
 * Single retention policy for recoverable business content.
 */
@Component
public class RecycleBinPolicy {

    private final int retentionDays;
    private final Clock applicationClock;

    public RecycleBinPolicy(
            @Value("${recycle-bin.retention-days:30}") int retentionDays,
            Clock applicationClock) {
        if (retentionDays < 1 || retentionDays > 365) {
            throw new IllegalArgumentException("recycle-bin.retention-days must be between 1 and 365");
        }
        this.retentionDays = retentionDays;
        this.applicationClock = applicationClock;
    }

    public int retentionDays() {
        return retentionDays;
    }

    public LocalDateTime restoreDeadline(LocalDateTime deletedAt) {
        return deletedAt.plusDays(retentionDays);
    }

    public LocalDateTime now() {
        return LocalDateTime.now(applicationClock);
    }
}
