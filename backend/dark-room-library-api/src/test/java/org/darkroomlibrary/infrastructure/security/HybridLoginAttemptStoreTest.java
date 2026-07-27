package org.darkroomlibrary.infrastructure.security;

import org.darkroomlibrary.infrastructure.cache.CacheService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HybridLoginAttemptStoreTest {

    @Mock
    private CacheService cacheService;

    @InjectMocks
    private HybridLoginAttemptStore store;

    @Test
    void redisBackedFailuresDoNotLeaveStaleLocalCounters() {
        when(cacheService.increment(anyString(), any(Duration.class)))
                .thenReturn(Optional.of(4L))
                .thenReturn(Optional.empty());
        when(cacheService.getString(anyString())).thenReturn(Optional.empty());

        store.loginFailed("reader", 5, 30);
        for (int i = 0; i < 4; i++) {
            store.loginFailed("reader", 5, 30);
        }

        assertFalse(store.isBlocked("reader"));
    }
}
