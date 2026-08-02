package org.darkroomlibrary.infrastructure.cache;

import java.time.Duration;
import java.util.Optional;

public interface CacheService {

    Optional<String> getString(String key);

    Optional<String> getAndDelete(String key);

    Optional<Boolean> compareAndDelete(String key, String expectedValue);

    boolean setString(String key, String value, Duration ttl);

    Optional<Boolean> setIfAbsent(String key, String value, Duration ttl);

    boolean delete(String key);

    Optional<Long> increment(String key, Duration ttl);

    Optional<Boolean> tryConsumeToken(String key, int capacity, Duration refillPeriod);
}
