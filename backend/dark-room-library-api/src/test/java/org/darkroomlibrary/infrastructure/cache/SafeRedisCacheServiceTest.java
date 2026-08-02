package org.darkroomlibrary.infrastructure.cache;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SafeRedisCacheServiceTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    private SafeRedisCacheService cacheService;

    @BeforeEach
    void setUp() {
        cacheService = new SafeRedisCacheService();
        ReflectionTestUtils.setField(cacheService, "enabled", true);
        ReflectionTestUtils.setField(cacheService, "recoveryIntervalMs", 60_000L);
        ReflectionTestUtils.setField(cacheService, "redisTemplate", redisTemplate);
    }

    @Test
    void skipsRepeatedRedisCallsDuringRecoveryWindow() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("test-key")).thenThrow(new IllegalStateException("redis unavailable"));

        assertTrue(cacheService.getString("test-key").isEmpty());
        assertTrue(cacheService.getString("test-key").isEmpty());

        verify(redisTemplate, times(1)).opsForValue();
        verify(valueOperations, times(1)).get("test-key");
    }

    @Test
    @SuppressWarnings("unchecked")
    void atomicallyGetsAndDeletesWithoutDependingOnRedisGetDel() {
        when(redisTemplate.execute(
                any(DefaultRedisScript.class),
                eq(List.of("captcha:test"))
        )).thenReturn("42");

        assertEquals(Optional.of("42"), cacheService.getAndDelete("captcha:test"));
    }

    @Test
    void replaysDeletesBeforeUsingRedisAfterRecovery() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("probe"))
                .thenThrow(new IllegalStateException("redis unavailable"))
                .thenReturn("ready");
        when(redisTemplate.delete("login:locked-until:reader")).thenReturn(true);

        assertTrue(cacheService.getString("probe").isEmpty());
        assertFalse(cacheService.delete("login:locked-until:reader"));

        AtomicLong unavailableUntil =
                (AtomicLong) ReflectionTestUtils.getField(cacheService, "unavailableUntil");
        unavailableUntil.set(0);

        assertEquals(Optional.of("ready"), cacheService.getString("probe"));
        var ordered = inOrder(redisTemplate, valueOperations);
        ordered.verify(valueOperations).get("probe");
        ordered.verify(redisTemplate).delete("login:locked-until:reader");
        ordered.verify(valueOperations).get("probe");
    }

    @Test
    void treatsMissingRedisKeyAsAnIdempotentDelete() {
        when(redisTemplate.delete("missing-key")).thenReturn(false);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("probe")).thenReturn(null);

        assertTrue(cacheService.delete("missing-key"));
        assertTrue(cacheService.getString("probe").isEmpty());

        verify(redisTemplate).delete("missing-key");
    }

    @Test
    @SuppressWarnings("unchecked")
    void consumesTokenThroughAtomicRedisScript() {
        when(redisTemplate.execute(
                any(DefaultRedisScript.class),
                eq(List.of("rate-limit:sensitive:login:ip:192.0.2.10")),
                eq("60"),
                eq("60000")
        )).thenReturn(1L);

        assertEquals(
                Optional.of(true),
                cacheService.tryConsumeToken(
                        "rate-limit:sensitive:login:ip:192.0.2.10",
                        60,
                        Duration.ofMinutes(1)));
    }
}
