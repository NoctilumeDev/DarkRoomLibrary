package org.darkroomlibrary.infrastructure.cache;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

@Slf4j
@Service
public class SafeRedisCacheService implements CacheService {

    private static final DefaultRedisScript<Long> COMPARE_AND_DELETE_SCRIPT =
            new DefaultRedisScript<>(
                    "if redis.call('get', KEYS[1]) == ARGV[1] "
                            + "then return redis.call('del', KEYS[1]) else return 0 end",
                    Long.class
            );
    private static final DefaultRedisScript<String> GET_AND_DELETE_SCRIPT =
            new DefaultRedisScript<>(
                    "local value = redis.call('get', KEYS[1]); "
                            + "if value then redis.call('del', KEYS[1]); end; "
                            + "return value",
                    String.class
            );
    private static final DefaultRedisScript<Long> INCREMENT_WITH_TTL_SCRIPT =
            new DefaultRedisScript<>(
                    "local value = redis.call('incr', KEYS[1]); "
                            + "if value == 1 then redis.call('pexpire', KEYS[1], ARGV[1]); end; "
                            + "return value",
                    Long.class
            );
    private static final DefaultRedisScript<Long> TOKEN_BUCKET_SCRIPT =
            new DefaultRedisScript<>(
                    "local time = redis.call('TIME'); "
                            + "local now = time[1] * 1000 + math.floor(time[2] / 1000); "
                            + "local capacity = tonumber(ARGV[1]); "
                            + "local refillPeriod = math.max(1, tonumber(ARGV[2])); "
                            + "local refillRate = capacity / refillPeriod; "
                            + "local values = redis.call('HMGET', KEYS[1], 'tokens', 'timestamp'); "
                            + "local tokens = tonumber(values[1]); "
                            + "local timestamp = tonumber(values[2]); "
                            + "if tokens == nil or timestamp == nil then "
                            + "  tokens = capacity; timestamp = now; "
                            + "else "
                            + "  tokens = math.min(capacity, tokens + math.max(0, now - timestamp) * refillRate); "
                            + "  timestamp = now; "
                            + "end; "
                            + "local allowed = 0; "
                            + "if tokens >= 1 then tokens = tokens - 1; allowed = 1; end; "
                            + "redis.call('HSET', KEYS[1], 'tokens', tokens, 'timestamp', timestamp); "
                            + "redis.call('PEXPIRE', KEYS[1], math.ceil(refillPeriod * 2)); "
                            + "return allowed",
                    Long.class
            );

    @Value("${middleware.redis.enabled:false}")
    private boolean enabled;

    @Value("${middleware.redis.recovery-interval-ms:30000}")
    private long recoveryIntervalMs;

    @Autowired
    private StringRedisTemplate redisTemplate;

    private final AtomicBoolean warned = new AtomicBoolean(false);
    private final AtomicLong unavailableUntil = new AtomicLong(0);
    private final Set<String> pendingDeletes = ConcurrentHashMap.newKeySet();
    private final Object recoveryLock = new Object();

    @Override
    public Optional<String> getString(String key) {
        if (!prepareForOperation()) {
            return Optional.empty();
        }
        try {
            Optional<String> result = Optional.ofNullable(redisTemplate.opsForValue().get(key));
            markAvailable();
            return result;
        } catch (Exception e) {
            markUnavailable(e);
            return Optional.empty();
        }
    }

    @Override
    public Optional<String> getAndDelete(String key) {
        if (!prepareForOperation()) {
            return Optional.empty();
        }
        try {
            Optional<String> result = Optional.ofNullable(redisTemplate.execute(
                    GET_AND_DELETE_SCRIPT,
                    List.of(key)
            ));
            markAvailable();
            return result;
        } catch (Exception e) {
            markUnavailable(e);
            return Optional.empty();
        }
    }

    @Override
    public Optional<Boolean> compareAndDelete(String key, String expectedValue) {
        if (!prepareForOperation()) {
            return Optional.empty();
        }
        try {
            Long deleted = redisTemplate.execute(
                    COMPARE_AND_DELETE_SCRIPT,
                    List.of(key),
                    expectedValue
            );
            Optional<Boolean> result = Optional.of(deleted != null && deleted > 0);
            markAvailable();
            return result;
        } catch (Exception e) {
            markUnavailable(e);
            return Optional.empty();
        }
    }

    @Override
    public boolean setString(String key, String value, Duration ttl) {
        if (!prepareForOperation()) {
            return false;
        }
        try {
            redisTemplate.opsForValue().set(key, value, ttl);
            markAvailable();
            return true;
        } catch (Exception e) {
            markUnavailable(e);
            return false;
        }
    }

    @Override
    public Optional<Boolean> setIfAbsent(String key, String value, Duration ttl) {
        if (!prepareForOperation()) {
            return Optional.empty();
        }
        try {
            Boolean stored = redisTemplate.opsForValue().setIfAbsent(key, value, ttl);
            Optional<Boolean> result = Optional.of(Boolean.TRUE.equals(stored));
            markAvailable();
            return result;
        } catch (Exception e) {
            markUnavailable(e);
            return Optional.empty();
        }
    }

    @Override
    public boolean delete(String key) {
        if (!enabled) {
            return false;
        }
        pendingDeletes.add(key);
        if (!prepareForOperation()) {
            return false;
        }
        return !pendingDeletes.contains(key);
    }

    private boolean deleteNow(String key) {
        try {
            redisTemplate.delete(key);
            markAvailable();
            return true;
        } catch (Exception e) {
            markUnavailable(e);
            return false;
        }
    }

    @Override
    public Optional<Long> increment(String key, Duration ttl) {
        if (!prepareForOperation()) {
            return Optional.empty();
        }
        try {
            long ttlMillis = ttl == null ? 1L : Math.max(1L, ttl.toMillis());
            Long value = redisTemplate.execute(
                    INCREMENT_WITH_TTL_SCRIPT,
                    List.of(key),
                    String.valueOf(ttlMillis)
            );
            Optional<Long> result = Optional.ofNullable(value);
            markAvailable();
            return result;
        } catch (Exception e) {
            markUnavailable(e);
            return Optional.empty();
        }
    }

    @Override
    public Optional<Boolean> tryConsumeToken(String key, int capacity, Duration refillPeriod) {
        if (!prepareForOperation()) {
            return Optional.empty();
        }
        try {
            int normalizedCapacity = Math.max(1, capacity);
            long refillPeriodMillis = refillPeriod == null
                    ? 1L
                    : Math.max(1L, refillPeriod.toMillis());
            Long allowed = redisTemplate.execute(
                    TOKEN_BUCKET_SCRIPT,
                    List.of(key),
                    String.valueOf(normalizedCapacity),
                    String.valueOf(refillPeriodMillis)
            );
            Optional<Boolean> result = Optional.of(allowed != null && allowed == 1L);
            markAvailable();
            return result;
        } catch (Exception e) {
            markUnavailable(e);
            return Optional.empty();
        }
    }

    private boolean shouldAttempt() {
        return enabled && System.currentTimeMillis() >= unavailableUntil.get();
    }

    private boolean prepareForOperation() {
        if (!shouldAttempt()) {
            return false;
        }
        if (pendingDeletes.isEmpty()) {
            return true;
        }
        synchronized (recoveryLock) {
            if (!shouldAttempt()) {
                return false;
            }
            for (String key : List.copyOf(pendingDeletes)) {
                if (!deleteNow(key)) {
                    return false;
                }
                pendingDeletes.remove(key);
            }
        }
        return true;
    }

    private void markAvailable() {
        unavailableUntil.set(0);
        warned.set(false);
    }

    private void markUnavailable(Exception e) {
        unavailableUntil.set(System.currentTimeMillis() + Math.max(1L, recoveryIntervalMs));
        if (warned.compareAndSet(false, true)) {
            log.warn("Redis不可用，已自动降级到本地/数据库兜底: {}", e.getMessage());
        }
    }
}
