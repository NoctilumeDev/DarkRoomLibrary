package org.darkroomlibrary.interceptor;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.darkroomlibrary.context.CurrentUserContext;
import org.darkroomlibrary.infrastructure.cache.CacheService;
import org.darkroomlibrary.infrastructure.security.ClientIpResolver;
import org.darkroomlibrary.web.response.ApiResponse;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.Writer;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Smooths bursts on security-sensitive endpoints while retaining a local fallback.
 */
public class TokenBucketRateLimitInterceptor implements HandlerInterceptor {

    public enum SubjectMode {
        IP_ONLY,
        AUTHENTICATED_OR_IP
    }

    private static final long CLEANUP_INTERVAL_NANOS = Duration.ofMinutes(10).toNanos();

    private final Map<String, LocalBucket> localBuckets = new ConcurrentHashMap<>();
    private final ClientIpResolver clientIpResolver;
    private final CacheService cacheService;
    private final ObjectMapper objectMapper;
    private final String keyNamespace;
    private final SubjectMode subjectMode;
    private final int capacity;
    private final Duration refillPeriod;
    private final long refillPeriodNanos;
    private volatile long lastCleanupNanos = System.nanoTime();

    public TokenBucketRateLimitInterceptor(ClientIpResolver clientIpResolver,
                                           CacheService cacheService,
                                           ObjectMapper objectMapper,
                                           String keyNamespace,
                                           SubjectMode subjectMode,
                                           int capacity,
                                           Duration refillPeriod) {
        this.clientIpResolver = clientIpResolver;
        this.cacheService = cacheService;
        this.objectMapper = objectMapper;
        this.keyNamespace = keyNamespace;
        this.subjectMode = subjectMode;
        this.capacity = Math.max(1, capacity);
        this.refillPeriod = refillPeriod == null || refillPeriod.isZero() || refillPeriod.isNegative()
                ? Duration.ofSeconds(1)
                : refillPeriod;
        this.refillPeriodNanos = Math.max(1L, this.refillPeriod.toNanos());
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }
        String subject = resolveSubject(request);
        String key = "rate-limit:sensitive:" + keyNamespace + ":" + subject;
        Optional<Boolean> distributedDecision = cacheService.tryConsumeToken(key, capacity, refillPeriod);
        boolean allowed = distributedDecision.orElseGet(() -> consumeLocal(subject));
        if (allowed) {
            return true;
        }

        response.setContentType("application/json;charset=UTF-8");
        response.setStatus(429);
        response.setHeader("Retry-After", String.valueOf(retryAfterSeconds()));
        Writer writer = response.getWriter();
        writer.write(objectMapper.writeValueAsString(
                new ApiResponse<Void>(429, "操作过于频繁，请稍后重试")));
        writer.flush();
        writer.close();
        return false;
    }

    private String resolveSubject(HttpServletRequest request) {
        Integer userId = subjectMode == SubjectMode.AUTHENTICATED_OR_IP
                ? CurrentUserContext.userId()
                : null;
        return userId == null
                ? "ip:" + clientIpResolver.resolve(request)
                : "user:" + userId;
    }

    private boolean consumeLocal(String subject) {
        long now = System.nanoTime();
        cleanupIfNecessary(now);
        LocalBucket bucket = localBuckets.computeIfAbsent(
                subject,
                ignored -> new LocalBucket(capacity, now));
        synchronized (bucket) {
            long elapsed = Math.max(0L, now - bucket.lastRefillNanos);
            bucket.tokens = Math.min(
                    capacity,
                    bucket.tokens + ((double) elapsed * capacity / refillPeriodNanos));
            bucket.lastRefillNanos = now;
            bucket.lastAccessNanos = now;
            if (bucket.tokens < 1D) {
                return false;
            }
            bucket.tokens -= 1D;
            return true;
        }
    }

    private void cleanupIfNecessary(long now) {
        if (now - lastCleanupNanos < CLEANUP_INTERVAL_NANOS) {
            return;
        }
        synchronized (localBuckets) {
            if (now - lastCleanupNanos < CLEANUP_INTERVAL_NANOS) {
                return;
            }
            lastCleanupNanos = now;
            long retention = Math.max(CLEANUP_INTERVAL_NANOS, refillPeriodNanos * 2);
            localBuckets.entrySet().removeIf(entry -> now - entry.getValue().lastAccessNanos > retention);
        }
    }

    private long retryAfterSeconds() {
        long nanosPerToken = Math.max(1L, refillPeriodNanos / capacity);
        return Math.max(1L, Duration.ofNanos(nanosPerToken).toSeconds());
    }

    private static class LocalBucket {
        double tokens;
        long lastRefillNanos;
        long lastAccessNanos;

        LocalBucket(int capacity, long now) {
            this.tokens = capacity;
            this.lastRefillNanos = now;
            this.lastAccessNanos = now;
        }
    }
}
