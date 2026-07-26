package org.darkroomlibrary.interceptor;

import org.darkroomlibrary.context.CurrentUserContext;
import org.darkroomlibrary.web.response.ApiResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.web.servlet.HandlerInterceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.Writer;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 请求频率限制拦截器
 * 固定窗口：匿名请求按 IP 限流，登录后按用户限流。
 */
public class RateLimitInterceptor implements HandlerInterceptor {

    private static final long WINDOW_MS = 60_000;
    private static final long CLEANUP_INTERVAL_MS = 600_000;

    private final Map<String, Window> ipWindows = new ConcurrentHashMap<>();
    private volatile long lastCleanup = System.currentTimeMillis();
    private final Object cleanupLock = new Object();
    private final boolean trustForwardedHeaders;
    private final int anonymousMaxRequestsPerMinute;
    private final int authenticatedMaxRequestsPerMinute;
    private final ObjectMapper objectMapper;

    public RateLimitInterceptor(boolean trustForwardedHeaders,
                                int anonymousMaxRequestsPerMinute,
                                int authenticatedMaxRequestsPerMinute,
                                ObjectMapper objectMapper) {
        this.trustForwardedHeaders = trustForwardedHeaders;
        this.anonymousMaxRequestsPerMinute = Math.max(1, anonymousMaxRequestsPerMinute);
        this.authenticatedMaxRequestsPerMinute = Math.max(1, authenticatedMaxRequestsPerMinute);
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }

        Integer userId = CurrentUserContext.userId();
        boolean authenticated = userId != null;
        String subject = authenticated ? "user:" + userId : "ip:" + getClientIp(request);
        int requestLimit = authenticated
                ? authenticatedMaxRequestsPerMinute
                : anonymousMaxRequestsPerMinute;
        long now = System.currentTimeMillis();

        // 定期清理过期IP窗口：双重检查 + 独立锁，避免与计数操作竞争
        if (now - lastCleanup > CLEANUP_INTERVAL_MS) {
            synchronized (cleanupLock) {
                if (now - lastCleanup > CLEANUP_INTERVAL_MS) {
                    lastCleanup = now;
                    ipWindows.entrySet().removeIf(e -> now - e.getValue().startTime > WINDOW_MS * 2);
                }
            }
        }

        // 原子获取或创建窗口，避免 computeIfAbsent + synchronized 的 TOCTOU 问题
        Window window = ipWindows.compute(subject, (key, existing) -> {
            if (existing == null) {
                return new Window(now, 1);
            }
            // 窗口内同步：同一IP的并发请求串行化计数
            synchronized (existing) {
                if (now - existing.startTime > WINDOW_MS) {
                    // 窗口已过期，旋转新窗口
                    return new Window(now, 1);
                }
                existing.count++;
            }
            return existing;
        });

        if (window.count > requestLimit) {
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
        if (trustForwardedHeaders) {
            String forwarded = request.getHeader("X-Forwarded-For");
            if (forwarded != null && !forwarded.isBlank()
                    && !"unknown".equalsIgnoreCase(forwarded)) {
                return forwarded.split(",")[0].trim();
            }
            String realIp = request.getHeader("X-Real-IP");
            if (realIp != null && !realIp.isBlank()
                    && !"unknown".equalsIgnoreCase(realIp)) {
                return realIp.trim();
            }
        }
        return request.getRemoteAddr();
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
