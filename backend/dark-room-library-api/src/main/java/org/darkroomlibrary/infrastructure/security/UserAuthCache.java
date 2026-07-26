package org.darkroomlibrary.infrastructure.security;

import org.darkroomlibrary.infrastructure.cache.CacheService;
import org.darkroomlibrary.mapper.UserMapper;
import org.darkroomlibrary.domain.type.AccountStatus;
import org.darkroomlibrary.domain.model.User;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import jakarta.annotation.Resource;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
public class UserAuthCache {

    private static final String KEY_PREFIX = "auth:user:";
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ConcurrentHashMap<Integer, CacheEntry> localCache = new ConcurrentHashMap<>();

    @Value("${auth.user-cache.ttl-seconds:30}")
    private long ttlSeconds;

    @Resource
    private CacheService cacheService;

    @Resource
    private UserMapper userMapper;

    public Optional<AuthUser> getActiveUser(Integer userId) {
        if (userId == null) {
            return Optional.empty();
        }
        long now = System.currentTimeMillis();
        CacheEntry local = localCache.get(userId);
        if (local != null && local.expireAt > now) {
            return Optional.of(local.authUser);
        }
        localCache.remove(userId);

        Optional<AuthUser> redisUser = getFromRedis(userId);
        if (redisUser.isPresent()) {
            putLocal(redisUser.get());
            return redisUser;
        }

        User user = userMapper.getByActive(User.builder().id(userId).build());
        if (user == null) {
            evict(userId);
            return Optional.empty();
        }
        AuthUser authUser = new AuthUser(
                user.getId(),
                user.getUserName(),
                user.getUserRole(),
                isDisabled(user)
        );
        putLocal(authUser);
        putRedis(authUser);
        return Optional.of(authUser);
    }

    public void evict(Integer userId) {
        if (userId == null) {
            return;
        }
        localCache.remove(userId);
        cacheService.delete(KEY_PREFIX + userId);
    }

    private Optional<AuthUser> getFromRedis(Integer userId) {
        Optional<String> value = cacheService.getString(KEY_PREFIX + userId);
        if (value.isEmpty()) {
            return Optional.empty();
        }
        try {
            return Optional.of(objectMapper.readValue(value.get(), AuthUser.class));
        } catch (Exception e) {
            log.warn("Auth user cache value is invalid, evicting: userId={}, error={}", userId, e.getMessage());
            cacheService.delete(KEY_PREFIX + userId);
            return Optional.empty();
        }
    }

    private void putRedis(AuthUser authUser) {
        try {
            cacheService.setString(
                    KEY_PREFIX + authUser.getId(),
                    objectMapper.writeValueAsString(authUser),
                    Duration.ofSeconds(ttlSeconds)
            );
        } catch (Exception e) {
            log.warn("Failed to serialize auth user cache: userId={}, error={}", authUser.getId(), e.getMessage());
        }
    }

    private void putLocal(AuthUser authUser) {
        localCache.put(authUser.getId(), new CacheEntry(authUser, System.currentTimeMillis() + ttlSeconds * 1000));
    }

    private boolean isDisabled(User user) {
        return Boolean.TRUE.equals(user.getIsLogin())
                || AccountStatus.FROZEN.code().equals(user.getAccountStatus())
                || AccountStatus.CANCELLED.code().equals(user.getAccountStatus());
    }

    private static class CacheEntry {
        final AuthUser authUser;
        final long expireAt;

        CacheEntry(AuthUser authUser, long expireAt) {
            this.authUser = authUser;
            this.expireAt = expireAt;
        }
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AuthUser {
        private Integer id;
        private String userName;
        private Integer userRole;
        private Boolean disabled;
    }
}
