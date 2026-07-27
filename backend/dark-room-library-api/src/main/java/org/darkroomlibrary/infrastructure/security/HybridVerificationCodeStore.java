package org.darkroomlibrary.infrastructure.security;

import org.darkroomlibrary.infrastructure.cache.CacheService;
import org.springframework.stereotype.Component;

import jakarta.annotation.Resource;
import java.time.Duration;
import java.time.LocalDate;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

@Component
public class HybridVerificationCodeStore implements VerificationCodeStore {

    private static final String CODE_PREFIX = "verification:code:";
    private static final String LAST_SEND_PREFIX = "verification:last-send:";
    private static final String DAILY_COUNT_PREFIX = "verification:daily-count:";

    @Resource
    private CacheService cacheService;

    private final Map<String, CodeEntry> codeMap = new ConcurrentHashMap<>();
    private final Map<String, SlotEntry> sendSlots = new ConcurrentHashMap<>();
    private final Map<String, CountEntry> dailySendCount = new ConcurrentHashMap<>();

    @Override
    public void putCode(String purpose, String email, String code, long ttlMillis) {
        String key = codeKey(purpose, email);
        boolean storedInRedis = cacheService.setString(
                CODE_PREFIX + key,
                code,
                Duration.ofMillis(ttlMillis)
        );
        if (storedInRedis) {
            codeMap.remove(key);
        } else {
            // Prevent an older Redis-backed code from becoming valid again after recovery.
            cacheService.delete(CODE_PREFIX + key);
            codeMap.put(key, new CodeEntry(code, System.currentTimeMillis() + ttlMillis));
        }
    }

    @Override
    public boolean consumeCode(String purpose, String email, String expectedCode) {
        String key = codeKey(purpose, email);
        if (codeMap.containsKey(key)) {
            return consumeLocalCode(key, expectedCode);
        }
        Optional<Boolean> redisResult = cacheService.compareAndDelete(
                CODE_PREFIX + key,
                expectedCode
        );
        if (redisResult.isPresent()) {
            return redisResult.get();
        }
        return consumeLocalCode(key, expectedCode);
    }

    private boolean consumeLocalCode(String key, String expectedCode) {
        AtomicBoolean consumed = new AtomicBoolean(false);
        long now = System.currentTimeMillis();
        codeMap.computeIfPresent(key, (ignored, entry) -> {
            if (now > entry.expireTime) {
                return null;
            }
            if (entry.code.equals(expectedCode)) {
                consumed.set(true);
                return null;
            }
            return entry;
        });
        return consumed.get();
    }

    @Override
    public void removeCode(String purpose, String email) {
        String key = codeKey(purpose, email);
        codeMap.remove(key);
        cacheService.delete(CODE_PREFIX + key);
    }

    @Override
    public boolean tryAcquireSendSlot(String email, String token, long ttlMillis) {
        long now = System.currentTimeMillis();
        SlotEntry local = sendSlots.get(email);
        if (local != null && now <= local.expireTime) {
            return false;
        }

        Optional<Boolean> redisResult = cacheService.setIfAbsent(
                LAST_SEND_PREFIX + email,
                token,
                Duration.ofMillis(ttlMillis));
        if (redisResult.isPresent()) {
            if (redisResult.get()) {
                sendSlots.put(email, new SlotEntry(token, now + ttlMillis));
            }
            return redisResult.get();
        }

        AtomicBoolean acquired = new AtomicBoolean(false);
        sendSlots.compute(email, (ignored, entry) -> {
            if (entry != null && now <= entry.expireTime) {
                return entry;
            }
            acquired.set(true);
            return new SlotEntry(token, now + ttlMillis);
        });
        return acquired.get();
    }

    @Override
    public void releaseSendSlot(String email, String token) {
        cacheService.compareAndDelete(LAST_SEND_PREFIX + email, token);
        sendSlots.computeIfPresent(email,
                (ignored, entry) -> entry.token.equals(token) ? null : entry);
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
        sendSlots.entrySet().removeIf(entry -> now > entry.getValue().expireTime);
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

    private static class SlotEntry {
        final String token;
        final long expireTime;

        SlotEntry(String token, long expireTime) {
            this.token = token;
            this.expireTime = expireTime;
        }
    }
}
