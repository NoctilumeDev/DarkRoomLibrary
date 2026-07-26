package org.darkroomlibrary.infrastructure.security;

import org.darkroomlibrary.infrastructure.cache.CacheService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import jakarta.annotation.Resource;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
public class HybridLoginAttemptStore implements LoginAttemptStore {

    private static final String COUNT_PREFIX = "login:fail-count:";
    private static final String LOCK_PREFIX = "login:locked-until:";

    @Resource
    private CacheService cacheService;

    private final Map<String, AttemptEntry> attemptMap = new ConcurrentHashMap<>();

    @Override
    public void loginFailed(String account, int maxFailAttempts, int lockDurationMinutes) {
        long now = System.currentTimeMillis();
        long lockMillis = lockDurationMinutes * 60L * 1000;
        AttemptEntry entry = attemptMap.computeIfAbsent(account, k -> new AttemptEntry());
        synchronized (entry) {
            if (entry.lastFailTime > 0 && now - entry.lastFailTime > lockMillis) {
                entry.failCount = 0;
                entry.lockedUntil = null;
            }
            entry.failCount++;
            entry.lastFailTime = now;
            entry.expireAfterMillis = lockMillis;
            if (entry.failCount >= maxFailAttempts) {
                entry.lockedUntil = now + lockMillis;
            }
        }

        Optional<Long> redisCount = cacheService.increment(
                COUNT_PREFIX + account,
                Duration.ofMillis(lockMillis)
        );
        if (redisCount.isPresent() && redisCount.get() >= maxFailAttempts) {
            cacheService.setString(LOCK_PREFIX + account, String.valueOf(now + lockMillis), Duration.ofMillis(lockMillis));
            log.warn("账户 {} 已被锁定，失败次数: {}", account, redisCount.get());
        } else if (entry.failCount >= maxFailAttempts) {
            log.warn("账户 {} 已被锁定，失败次数: {}", account, entry.failCount);
        }
    }

    @Override
    public void loginSucceeded(String account) {
        attemptMap.remove(account);
        cacheService.delete(COUNT_PREFIX + account);
        cacheService.delete(LOCK_PREFIX + account);
    }

    @Override
    public boolean isBlocked(String account) {
        Long lockedUntil = getLockedUntil(account);
        if (lockedUntil == null) {
            return false;
        }
        if (System.currentTimeMillis() > lockedUntil) {
            loginSucceeded(account);
            return false;
        }
        return true;
    }

    @Override
    public long getRemainingLockSeconds(String account) {
        Long lockedUntil = getLockedUntil(account);
        if (lockedUntil == null) {
            return 0;
        }
        long remaining = lockedUntil - System.currentTimeMillis();
        return remaining > 0 ? remaining / 1000 : 0;
    }

    @Override
    public void clearExpired() {
        long now = System.currentTimeMillis();
        attemptMap.entrySet().removeIf(entry -> {
            AttemptEntry val = entry.getValue();
            if (val.lockedUntil != null) {
                return now > val.lockedUntil;
            }
            return val.lastFailTime > 0 && val.expireAfterMillis > 0
                    && now - val.lastFailTime > val.expireAfterMillis;
        });
    }

    private Long getLockedUntil(String account) {
        Optional<String> redisValue = cacheService.getString(LOCK_PREFIX + account);
        if (redisValue.isPresent()) {
            try {
                return Long.parseLong(redisValue.get());
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        AttemptEntry entry = attemptMap.get(account);
        return entry == null ? null : entry.lockedUntil;
    }

    private static class AttemptEntry {
        int failCount;
        long lastFailTime;
        long expireAfterMillis;
        Long lockedUntil;
    }
}
