package org.darkroomlibrary.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.darkroomlibrary.infrastructure.cache.CacheService;
import org.darkroomlibrary.infrastructure.security.UserAuthLookup;
import org.darkroomlibrary.interceptor.JwtInterceptor;
import org.darkroomlibrary.interceptor.RateLimitInterceptor;
import org.darkroomlibrary.utils.JwtUtil;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Configures authentication and request-rate interceptors.
 */
@Configuration
public class InterceptorConfig implements WebMvcConfigurer {

    private final String apiPrefix;
    private final boolean trustForwardedHeaders;
    private final int anonymousMaxRequestsPerMinute;
    private final int authenticatedMaxRequestsPerMinute;
    private final CacheService cacheService;
    private final UserAuthLookup userAuthLookup;
    private final ObjectMapper objectMapper;
    private final JwtUtil jwtUtil;

    public InterceptorConfig(
            @Value("${app.api-prefix}") String apiPrefix,
            @Value("${security.rate-limit.trust-forwarded-headers:false}") boolean trustForwardedHeaders,
            @Value("${security.rate-limit.anonymous-max-per-minute:60}") int anonymousMaxRequestsPerMinute,
            @Value("${security.rate-limit.authenticated-max-per-minute:300}") int authenticatedMaxRequestsPerMinute,
            CacheService cacheService,
            UserAuthLookup userAuthLookup,
            ObjectMapper objectMapper,
            JwtUtil jwtUtil) {
        this.apiPrefix = apiPrefix;
        this.trustForwardedHeaders = trustForwardedHeaders;
        this.anonymousMaxRequestsPerMinute = anonymousMaxRequestsPerMinute;
        this.authenticatedMaxRequestsPerMinute = authenticatedMaxRequestsPerMinute;
        this.cacheService = cacheService;
        this.userAuthLookup = userAuthLookup;
        this.objectMapper = objectMapper;
        this.jwtUtil = jwtUtil;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new JwtInterceptor(apiPrefix, userAuthLookup, objectMapper, jwtUtil))
                .addPathPatterns("/**")
                .excludePathPatterns(
                        apiPrefix + "/user/login",
                        apiPrefix + "/user/register",
                        apiPrefix + "/user/resetPwd",
                        apiPrefix + "/user/sendVerifyCode",
                        apiPrefix + "/file/getFile",
                        apiPrefix + "/file/public",
                        apiPrefix + "/captcha/generate",
                        apiPrefix + "/captcha/verify",
                        apiPrefix + "/error"
                );

        registry.addInterceptor(new RateLimitInterceptor(
                        trustForwardedHeaders,
                        anonymousMaxRequestsPerMinute,
                        authenticatedMaxRequestsPerMinute,
                        cacheService,
                        objectMapper))
                .addPathPatterns("/**")
                .excludePathPatterns(
                        apiPrefix + "/file/getFile",
                        apiPrefix + "/file/public"
                );
    }
}
