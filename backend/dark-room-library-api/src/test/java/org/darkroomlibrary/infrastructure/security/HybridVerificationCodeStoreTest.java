package org.darkroomlibrary.infrastructure.security;

import org.darkroomlibrary.infrastructure.cache.CacheService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HybridVerificationCodeStoreTest {

    @Mock
    private CacheService cacheService;

    @InjectMocks
    private HybridVerificationCodeStore store;

    @Test
    void localFallbackConsumesOnlyTheMatchingCode() {
        when(cacheService.setString(anyString(), anyString(), any(Duration.class))).thenReturn(false);
        when(cacheService.compareAndDelete(anyString(), anyString())).thenReturn(Optional.empty());
        store.putCode("REGISTER", "reader@example.com", "731942", 300_000);

        assertFalse(store.consumeCode("REGISTER", "reader@example.com", "000000"));
        assertTrue(store.consumeCode("REGISTER", "reader@example.com", "731942"));
        assertFalse(store.consumeCode("REGISTER", "reader@example.com", "731942"));
    }

    @Test
    void localFallbackQueuesDeletionOfAnyOlderRedisCode() {
        when(cacheService.setString(anyString(), anyString(), any(Duration.class))).thenReturn(false);

        store.putCode("REGISTER", "reader@example.com", "731942", 300_000);

        verify(cacheService).delete("verification:code:REGISTER:reader@example.com");
    }

    @Test
    void redisBackedCodeUsesAtomicComparisonAndDeletion() {
        when(cacheService.setString(anyString(), anyString(), any(Duration.class))).thenReturn(true);
        when(cacheService.compareAndDelete("verification:code:RESET_PASSWORD:reader@example.com", "731942"))
                .thenReturn(Optional.of(true));
        store.putCode("RESET_PASSWORD", "reader@example.com", "731942", 300_000);

        assertTrue(store.consumeCode("RESET_PASSWORD", "reader@example.com", "731942"));
        verify(cacheService).compareAndDelete(
                "verification:code:RESET_PASSWORD:reader@example.com",
                "731942"
        );
    }

    @Test
    void localFallbackAcquiresOnlyOneConcurrentSendSlot() {
        when(cacheService.setIfAbsent(anyString(), anyString(), any(Duration.class)))
                .thenReturn(Optional.empty());

        assertTrue(store.tryAcquireSendSlot("reader@example.com", "slot-a", 60_000));
        assertFalse(store.tryAcquireSendSlot("reader@example.com", "slot-b", 60_000));
    }

    @Test
    void localFallbackSerializesCompetingSendSlotsAfterConcurrentRedisMisses() throws Exception {
        CountDownLatch bothRequestsReachedRedisFallback = new CountDownLatch(2);
        when(cacheService.setIfAbsent(anyString(), anyString(), any(Duration.class)))
                .thenAnswer(ignored -> {
                    bothRequestsReachedRedisFallback.countDown();
                    assertTrue(bothRequestsReachedRedisFallback.await(10, TimeUnit.SECONDS));
                    return Optional.empty();
                });
        ExecutorService executor = Executors.newFixedThreadPool(2);

        try {
            Future<Boolean> first = executor.submit(
                    () -> store.tryAcquireSendSlot("reader@example.com", "slot-a", 60_000));
            Future<Boolean> second = executor.submit(
                    () -> store.tryAcquireSendSlot("reader@example.com", "slot-b", 60_000));

            assertTrue(first.get(10, TimeUnit.SECONDS) ^ second.get(10, TimeUnit.SECONDS));
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void releaseUsesTokenComparison() {
        when(cacheService.setIfAbsent(anyString(), anyString(), any(Duration.class)))
                .thenReturn(Optional.empty());
        when(cacheService.compareAndDelete(anyString(), anyString())).thenReturn(Optional.empty());
        assertTrue(store.tryAcquireSendSlot("reader@example.com", "slot-a", 60_000));

        store.releaseSendSlot("reader@example.com", "slot-b");

        assertFalse(store.tryAcquireSendSlot("reader@example.com", "slot-c", 60_000));
    }
}
