package org.darkroomlibrary.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.darkroomlibrary.infrastructure.cache.CacheService;
import org.darkroomlibrary.infrastructure.security.ClientIpResolver;
import org.darkroomlibrary.infrastructure.security.UserAuthLookup;
import org.darkroomlibrary.interceptor.JwtInterceptor;
import org.darkroomlibrary.interceptor.RateLimitInterceptor;
import org.darkroomlibrary.interceptor.TokenBucketRateLimitInterceptor;
import org.darkroomlibrary.utils.JwtUtil;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.time.Duration;

/**
 * Configures authentication and request-rate interceptors.
 */
@Configuration
public class InterceptorConfig implements WebMvcConfigurer {

    private static final String FILE_GET_PATH = "/file/getFile";
    private static final String FILE_PUBLIC_PATH = "/file/public";
    private static final String HEALTH_LIVE_PATH = "/health/live";
    private static final String HEALTH_READY_PATH = "/health/ready";

    private final String apiPrefix;
    private final ClientIpResolver clientIpResolver;
    private final int ingressMaxRequestsPerMinute;
    private final int publicFileMaxRequestsPerMinute;
    private final int anonymousMaxRequestsPerMinute;
    private final int authenticatedMaxRequestsPerMinute;
    private final int loginBucketCapacity;
    private final int accountBucketCapacity;
    private final int verificationBucketCapacity;
    private final int captchaBucketCapacity;
    private final CacheService cacheService;
    private final UserAuthLookup userAuthLookup;
    private final ObjectMapper objectMapper;
    private final JwtUtil jwtUtil;

    public InterceptorConfig(
            @Value("${app.api-prefix}") String apiPrefix,
            @Value("${security.rate-limit.ingress-max-per-minute:1200}") int ingressMaxRequestsPerMinute,
            @Value("${security.rate-limit.public-file-max-per-minute:600}") int publicFileMaxRequestsPerMinute,
            @Value("${security.rate-limit.anonymous-max-per-minute:60}") int anonymousMaxRequestsPerMinute,
            @Value("${security.rate-limit.authenticated-max-per-minute:300}") int authenticatedMaxRequestsPerMinute,
            @Value("${security.sensitive-rate-limit.login-capacity:60}") int loginBucketCapacity,
            @Value("${security.sensitive-rate-limit.account-capacity:12}") int accountBucketCapacity,
            @Value("${security.sensitive-rate-limit.verification-capacity:6}") int verificationBucketCapacity,
            @Value("${security.sensitive-rate-limit.captcha-capacity:90}") int captchaBucketCapacity,
            CacheService cacheService,
            ClientIpResolver clientIpResolver,
            UserAuthLookup userAuthLookup,
            ObjectMapper objectMapper,
            JwtUtil jwtUtil) {
        this.apiPrefix = apiPrefix;
        this.clientIpResolver = clientIpResolver;
        this.ingressMaxRequestsPerMinute = ingressMaxRequestsPerMinute;
        this.publicFileMaxRequestsPerMinute = publicFileMaxRequestsPerMinute;
        this.anonymousMaxRequestsPerMinute = anonymousMaxRequestsPerMinute;
        this.authenticatedMaxRequestsPerMinute = authenticatedMaxRequestsPerMinute;
        this.loginBucketCapacity = loginBucketCapacity;
        this.accountBucketCapacity = accountBucketCapacity;
        this.verificationBucketCapacity = verificationBucketCapacity;
        this.captchaBucketCapacity = captchaBucketCapacity;
        this.cacheService = cacheService;
        this.userAuthLookup = userAuthLookup;
        this.objectMapper = objectMapper;
        this.jwtUtil = jwtUtil;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new RateLimitInterceptor(
                        clientIpResolver,
                        ingressMaxRequestsPerMinute,
                        ingressMaxRequestsPerMinute,
                        cacheService,
                        objectMapper,
                        "ingress",
                        RateLimitInterceptor.SubjectMode.IP_ONLY))
                .addPathPatterns("/**")
                .excludePathPatterns(
                        HEALTH_LIVE_PATH,
                        HEALTH_READY_PATH
                )
                .order(0);

        registry.addInterceptor(new JwtInterceptor(apiPrefix, userAuthLookup, objectMapper, jwtUtil))
                .addPathPatterns("/**")
                .excludePathPatterns(
                        "/user/login",
                        "/user/register",
                        "/user/resetPwd",
                        "/user/sendVerifyCode",
                        FILE_GET_PATH,
                        FILE_PUBLIC_PATH,
                        "/captcha/generate",
                        "/captcha/verify",
                        HEALTH_LIVE_PATH,
                        HEALTH_READY_PATH,
                        "/error"
                )
                .order(1);

        registry.addInterceptor(tokenBucket(
                        "login",
                        TokenBucketRateLimitInterceptor.SubjectMode.IP_ONLY,
                        loginBucketCapacity,
                        Duration.ofMinutes(1)))
                .addPathPatterns("/user/login")
                .order(2);

        registry.addInterceptor(tokenBucket(
                        "account",
                        TokenBucketRateLimitInterceptor.SubjectMode.IP_ONLY,
                        accountBucketCapacity,
                        Duration.ofMinutes(10)))
                .addPathPatterns("/user/register", "/user/resetPwd")
                .order(2);

        registry.addInterceptor(tokenBucket(
                        "verification",
                        TokenBucketRateLimitInterceptor.SubjectMode.AUTHENTICATED_OR_IP,
                        verificationBucketCapacity,
                        Duration.ofMinutes(10)))
                .addPathPatterns("/user/sendVerifyCode", "/user/sendEmailChangeCode")
                .order(2);

        registry.addInterceptor(tokenBucket(
                        "captcha",
                        TokenBucketRateLimitInterceptor.SubjectMode.IP_ONLY,
                        captchaBucketCapacity,
                        Duration.ofMinutes(1)))
                .addPathPatterns("/captcha/generate", "/captcha/verify")
                .order(2);

        registry.addInterceptor(new RateLimitInterceptor(
                        clientIpResolver,
                        publicFileMaxRequestsPerMinute,
                        publicFileMaxRequestsPerMinute,
                        cacheService,
                        objectMapper,
                        "public-file",
                        RateLimitInterceptor.SubjectMode.IP_ONLY))
                .addPathPatterns(
                        FILE_GET_PATH,
                        FILE_PUBLIC_PATH
                )
                .order(3);

        registry.addInterceptor(new RateLimitInterceptor(
                        clientIpResolver,
                        anonymousMaxRequestsPerMinute,
                        authenticatedMaxRequestsPerMinute,
                        cacheService,
                        objectMapper))
                .addPathPatterns("/**")
                .excludePathPatterns(
                        FILE_GET_PATH,
                        FILE_PUBLIC_PATH,
                        HEALTH_LIVE_PATH,
                        HEALTH_READY_PATH
                )
                .order(4);
    }

    private TokenBucketRateLimitInterceptor tokenBucket(
            String namespace,
            TokenBucketRateLimitInterceptor.SubjectMode subjectMode,
            int capacity,
            Duration refillPeriod) {
        return new TokenBucketRateLimitInterceptor(
                clientIpResolver,
                cacheService,
                objectMapper,
                namespace,
                subjectMode,
                capacity,
                refillPeriod);
    }
}
