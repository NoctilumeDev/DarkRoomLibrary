package org.darkroomlibrary.infrastructure.security;

import org.darkroomlibrary.infrastructure.cache.CacheService;
import org.springframework.stereotype.Component;

import jakarta.annotation.Resource;
import java.time.Duration;
import java.time.LocalDate;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class HybridVerificationCodeStore implements VerificationCodeStore {

    private static final String CODE_PREFIX = "verification:code:";
    private static final String LAST_SEND_PREFIX = "verification:last-send:";
    private static final String DAILY_COUNT_PREFIX = "verification:daily-count:";

    @Resource
    private CacheService cacheService;

    private final Map<String, CodeEntry> codeMap = new ConcurrentHashMap<>();
    private final Map<String, CountEntry> lastSendTime = new ConcurrentHashMap<>();
    private final Map<String, CountEntry> dailySendCount = new ConcurrentHashMap<>();

    @Override
    public void putCode(String purpose, String email, String code, long ttlMillis) {
        String key = codeKey(purpose, email);
        codeMap.put(key, new CodeEntry(code, System.currentTimeMillis() + ttlMillis));
        cacheService.setString(CODE_PREFIX + key, code, Duration.ofMillis(ttlMillis));
    }

    @Override
    public Optional<String> getCode(String purpose, String email) {
        String key = codeKey(purpose, email);
        Optional<String> redisValue = cacheService.getString(CODE_PREFIX + key);
        if (redisValue.isPresent()) {
            return redisValue;
        }
        CodeEntry entry = codeMap.get(key);
        if (entry == null) {
            return Optional.empty();
        }
        if (System.currentTimeMillis() > entry.expireTime) {
            codeMap.remove(key);
            return Optional.empty();
        }
        return Optional.of(entry.code);
    }

    @Override
    public void removeCode(String purpose, String email) {
        String key = codeKey(purpose, email);
        codeMap.remove(key);
        cacheService.delete(CODE_PREFIX + key);
    }

    @Override
    public Optional<Long> getLastSendTime(String email) {
        Optional<String> redisValue = cacheService.getString(LAST_SEND_PREFIX + email);
        if (redisValue.isPresent()) {
            try {
                return Optional.of(Long.parseLong(redisValue.get()));
            } catch (NumberFormatException ignored) {
                return Optional.empty();
            }
        }
        CountEntry entry = lastSendTime.get(email);
        if (entry == null) {
            return Optional.empty();
        }
        if (System.currentTimeMillis() > entry.expireTime) {
            lastSendTime.remove(email);
            return Optional.empty();
        }
        return Optional.of(entry.count);
    }

    @Override
    public void putLastSendTime(String email, long timestamp, long ttlMillis) {
        lastSendTime.put(email, new CountEntry(timestamp, System.currentTimeMillis() + ttlMillis));
        cacheService.setString(LAST_SEND_PREFIX + email, String.valueOf(timestamp), Duration.ofMillis(ttlMillis));
    }

    @Override
    public long incrementDailySendCount(String email, long ttlMillis) {
        String key = LocalDate.now() + ":" + email;
        long expireTime = System.currentTimeMillis() + ttlMillis;
        CountEntry localCount = dailySendCount.compute(key, (ignored, entry) -> {
            if (entry == null || System.currentTimeMillis() > entry.expireTime) {
                return new CountEntry(1L, expireTime);
            }
            return new CountEntry(entry.count + 1, entry.expireTime);
        });
        Optional<Long> redisCount = cacheService.increment(DAILY_COUNT_PREFIX + key, Duration.ofMillis(ttlMillis));
        return Math.max(localCount.count, redisCount.orElse(0L));
    }

    @Override
    public void clearExpired() {
        long now = System.currentTimeMillis();
        codeMap.entrySet().removeIf(entry -> now > entry.getValue().expireTime);
        lastSendTime.entrySet().removeIf(entry -> now > entry.getValue().expireTime);
        dailySendCount.entrySet().removeIf(entry -> now > entry.getValue().expireTime);
    }

    private String codeKey(String purpose, String email) {
        return purpose + ":" + email;
    }

    private static class CodeEntry {
        final String code;
        final long expireTime;

        CodeEntry(String code, long expireTime) {
            this.code = code;
            this.expireTime = expireTime;
        }
    }

    private static class CountEntry {
        final long count;
        final long expireTime;

        CountEntry(long count, long expireTime) {
            this.count = count;
            this.expireTime = expireTime;
        }
    }
}
