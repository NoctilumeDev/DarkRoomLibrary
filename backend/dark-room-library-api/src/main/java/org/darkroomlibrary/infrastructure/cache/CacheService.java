package org.darkroomlibrary.infrastructure.cache;

import java.time.Duration;
import java.util.Optional;

public interface CacheService {

    Optional<String> getString(String key);

    boolean setString(String key, String value, Duration ttl);

    boolean delete(String key);

    Optional<Long> increment(String key, Duration ttl);
}
