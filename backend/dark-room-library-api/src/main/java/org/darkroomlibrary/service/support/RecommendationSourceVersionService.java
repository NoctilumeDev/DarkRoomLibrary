package org.darkroomlibrary.service.support;

import jakarta.annotation.Resource;
import org.darkroomlibrary.infrastructure.cache.CacheService;
import org.darkroomlibrary.utils.TransactionCallbacks;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

@Service
public class RecommendationSourceVersionService {

    private static final String GLOBAL_KEY = "recommendation:source:global";
    private static final String USER_KEY_PREFIX = "recommendation:source:user:";
    private static final Duration VERSION_TTL = Duration.ofDays(3650);

    @Resource
    private CacheService cacheService;

    public Optional<String> currentSeed(Integer userId) {
        if (userId == null) return Optional.empty();
        Optional<String> globalVersion = getOrCreate(GLOBAL_KEY);
        if (globalVersion.isEmpty()) return Optional.empty();
        Optional<String> userVersion = getOrCreate(userKey(userId));
        return userVersion.map(value -> globalVersion.get() + '|' + value);
    }

    public void invalidateGlobalAfterCommit() {
        afterCommit(GLOBAL_KEY);
    }

    public void invalidateUserAfterCommit(Integer userId) {
        if (userId != null) afterCommit(userKey(userId));
    }

    public void invalidateUserAndGlobalAfterCommit(Integer userId) {
        TransactionCallbacks.afterCommit(() -> {
            cacheService.delete(GLOBAL_KEY);
            if (userId != null) cacheService.delete(userKey(userId));
        });
    }

    private Optional<String> getOrCreate(String key) {
        Optional<String> existing = cacheService.getString(key);
        if (existing.isPresent()) return existing;
        String candidate = UUID.randomUUID().toString();
        Optional<Boolean> stored = cacheService.setIfAbsent(key, candidate, VERSION_TTL);
        if (stored.isEmpty()) return Optional.empty();
        return Boolean.TRUE.equals(stored.get())
                ? Optional.of(candidate) : cacheService.getString(key);
    }

    private void afterCommit(String key) {
        TransactionCallbacks.afterCommit(() -> cacheService.delete(key));
    }

    private String userKey(Integer userId) {
        return USER_KEY_PREFIX + userId;
    }
}
