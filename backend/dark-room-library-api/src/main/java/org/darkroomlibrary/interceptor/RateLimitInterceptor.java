package org.darkroomlibrary.interceptor;

import org.darkroomlibrary.context.CurrentUserContext;
import org.darkroomlibrary.infrastructure.cache.CacheService;
import org.darkroomlibrary.infrastructure.security.ClientIpResolver;
import org.darkroomlibrary.web.response.ApiResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.web.servlet.HandlerInterceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.Writer;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 请求频率限制拦截器
 * 固定窗口：匿名请求按 IP 限流，登录后按用户限流。
 */
public class RateLimitInterceptor implements HandlerInterceptor {

    private static final long WINDOW_MS = 60_000;
    private static final long CLEANUP_INTERVAL_MS = 600_000;

    public enum SubjectMode {
        IP_ONLY,
        AUTHENTICATED_OR_IP
    }

    private final Map<String, Window> ipWindows = new ConcurrentHashMap<>();
    private volatile long lastCleanup = System.currentTimeMillis();
    private final Object cleanupLock = new Object();
    private final boolean trustForwardedHeaders;
    private final int anonymousMaxRequestsPerMinute;
    private final int authenticatedMaxRequestsPerMinute;
    private final CacheService cacheService;
    private final ObjectMapper objectMapper;
    private final String keyNamespace;
    private final SubjectMode subjectMode;

    public RateLimitInterceptor(boolean trustForwardedHeaders,
                                int anonymousMaxRequestsPerMinute,
                                int authenticatedMaxRequestsPerMinute,
                                CacheService cacheService,
                                ObjectMapper objectMapper) {
        this(
                trustForwardedHeaders,
                anonymousMaxRequestsPerMinute,
                authenticatedMaxRequestsPerMinute,
                cacheService,
                objectMapper,
                "subject",
                SubjectMode.AUTHENTICATED_OR_IP
        );
    }

    public RateLimitInterceptor(boolean trustForwardedHeaders,
                                int anonymousMaxRequestsPerMinute,
                                int authenticatedMaxRequestsPerMinute,
                                CacheService cacheService,
                                ObjectMapper objectMapper,
                                String keyNamespace,
                                SubjectMode subjectMode) {
        this.trustForwardedHeaders = trustForwardedHeaders;
        this.anonymousMaxRequestsPerMinute = Math.max(1, anonymousMaxRequestsPerMinute);
        this.authenticatedMaxRequestsPerMinute = Math.max(1, authenticatedMaxRequestsPerMinute);
        this.cacheService = cacheService;
        this.objectMapper = objectMapper;
        this.keyNamespace = keyNamespace;
        this.subjectMode = subjectMode;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }

        Integer userId = subjectMode == SubjectMode.IP_ONLY ? null : CurrentUserContext.userId();
        boolean authenticated = subjectMode == SubjectMode.AUTHENTICATED_OR_IP && userId != null;
        String subject = authenticated ? "user:" + userId : "ip:" + getClientIp(request);
        int requestLimit = authenticated
                ? authenticatedMaxRequestsPerMinute
                : anonymousMaxRequestsPerMinute;
        long now = System.currentTimeMillis();
        long windowId = now / WINDOW_MS;
        String windowKey = windowId + ":" + subject;

        // 定期清理过期IP窗口：双重检查 + 独立锁，避免与计数操作竞争
        if (now - lastCleanup > CLEANUP_INTERVAL_MS) {
            synchronized (cleanupLock) {
                if (now - lastCleanup > CLEANUP_INTERVAL_MS) {
                    lastCleanup = now;
                    ipWindows.entrySet().removeIf(e -> now - e.getValue().startTime > WINDOW_MS * 2);
                }
            }
        }

        // 本地计数用于 Redis 未启用或临时不可用时降级。
        Window window = ipWindows.compute(windowKey, (key, existing) -> {
            if (existing == null) {
                return new Window(windowId * WINDOW_MS, 1);
            }
            synchronized (existing) {
                existing.count++;
            }
            return existing;
        });

        Optional<Long> distributedCount = cacheService.increment(
                "rate-limit:" + keyNamespace + ":" + windowKey,
                Duration.ofMillis(WINDOW_MS * 2)
        );
        long requestCount = Math.max(window.count, distributedCount.orElse(0L));
        if (requestCount > requestLimit) {
            response.setContentType("application/json;charset=UTF-8");
            response.setStatus(429);
            Writer writer = response.getWriter();
            ApiResponse<Void> error = new ApiResponse<>(429, "请求过于频繁，请稍后重试");
            writer.write(objectMapper.writeValueAsString(error));
            writer.flush();
            writer.close();
            return false;
        }
        return true;
    }

    private String getClientIp(HttpServletRequest request) {
        return ClientIpResolver.resolve(request, trustForwardedHeaders);
    }

    private static class Window {
        final long startTime;
        int count;

        Window(long startTime, int count) {
            this.startTime = startTime;
            this.count = count;
        }
    }
}
