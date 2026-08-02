package org.darkroomlibrary.infrastructure.security;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Resolves the request source consistently for ingress limits and login protection.
 */
public final class ClientIpResolver {

    private static final String UNKNOWN = "unknown";

    private ClientIpResolver() {
    }

    public static String resolve(HttpServletRequest request, boolean trustForwardedHeaders) {
        if (request == null) {
            return UNKNOWN;
        }
        if (trustForwardedHeaders) {
            String forwarded = firstAddress(request.getHeader("X-Forwarded-For"));
            if (forwarded != null) {
                return forwarded;
            }
            String realIp = normalized(request.getHeader("X-Real-IP"));
            if (realIp != null) {
                return realIp;
            }
        }
        String remoteAddress = normalized(request.getRemoteAddr());
        return remoteAddress == null ? UNKNOWN : remoteAddress;
    }

    public static String resolveCurrentRequest(boolean trustForwardedHeaders) {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
            return resolve(attributes.getRequest(), trustForwardedHeaders);
        }
        return UNKNOWN;
    }

    private static String firstAddress(String value) {
        if (value == null) {
            return null;
        }
        return normalized(value.split(",", 2)[0]);
    }

    private static String normalized(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        if (normalized.isEmpty() || "unknown".equalsIgnoreCase(normalized)) {
            return null;
        }
        return normalized;
    }
}
