package org.darkroomlibrary.infrastructure.cache;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
@Service
public class SafeRedisCacheService implements CacheService {

    @Value("${middleware.redis.enabled:false}")
    private boolean enabled;

    @Autowired
    private StringRedisTemplate redisTemplate;

    private final AtomicBoolean warned = new AtomicBoolean(false);

    @Override
    public Optional<String> getString(String key) {
        if (!enabled) {
            return Optional.empty();
        }
        try {
            return Optional.ofNullable(redisTemplate.opsForValue().get(key));
        } catch (Exception e) {
            warnOnce(e);
            return Optional.empty();
        }
    }

    @Override
    public boolean setString(String key, String value, Duration ttl) {
        if (!enabled) {
            return false;
        }
        try {
            redisTemplate.opsForValue().set(key, value, ttl);
            return true;
        } catch (Exception e) {
            warnOnce(e);
            return false;
        }
    }

    @Override
    public boolean delete(String key) {
        if (!enabled) {
            return false;
        }
        try {
            redisTemplate.delete(key);
            return true;
        } catch (Exception e) {
            warnOnce(e);
            return false;
        }
    }

    @Override
    public Optional<Long> increment(String key, Duration ttl) {
        if (!enabled) {
            return Optional.empty();
        }
        try {
            Long value = redisTemplate.opsForValue().increment(key);
            if (value != null && value == 1L) {
                redisTemplate.expire(key, ttl);
            }
            return Optional.ofNullable(value);
        } catch (Exception e) {
            warnOnce(e);
            return Optional.empty();
        }
    }

    private void warnOnce(Exception e) {
        if (warned.compareAndSet(false, true)) {
            log.warn("Redis不可用，已自动降级到本地/数据库兜底: {}", e.getMessage());
        }
    }
}
