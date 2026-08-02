package org.darkroomlibrary.interceptor;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.darkroomlibrary.context.CurrentUserContext;
import org.darkroomlibrary.infrastructure.cache.CacheService;
import org.darkroomlibrary.infrastructure.security.ClientIpResolver;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.time.Duration;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TokenBucketRateLimitInterceptorTest {

    @AfterEach
    void clearContext() {
        CurrentUserContext.clear();
    }

    @Test
    void usesLocalBucketWhenRedisIsUnavailable() throws Exception {
        CacheService cacheService = unavailableCache();
        TokenBucketRateLimitInterceptor interceptor = interceptor(
                cacheService,
                TokenBucketRateLimitInterceptor.SubjectMode.IP_ONLY,
                2);

        assertTrue(interceptor.preHandle(request("192.0.2.10"), response(), new Object()));
        assertTrue(interceptor.preHandle(request("192.0.2.10"), response(), new Object()));

        MockHttpServletResponse blocked = response();
        assertFalse(interceptor.preHandle(request("192.0.2.10"), blocked, new Object()));
        assertEquals(429, blocked.getStatus());
        assertEquals("30", blocked.getHeader("Retry-After"));
    }

    @Test
    void honorsDistributedRejectionWithoutUsingLocalAllowance() throws Exception {
        CacheService cacheService = mock(CacheService.class);
        when(cacheService.tryConsumeToken(anyString(), anyInt(), any()))
                .thenReturn(Optional.of(false));
        TokenBucketRateLimitInterceptor interceptor = interceptor(
                cacheService,
                TokenBucketRateLimitInterceptor.SubjectMode.IP_ONLY,
                60);

        MockHttpServletResponse blocked = response();
        assertFalse(interceptor.preHandle(request("192.0.2.10"), blocked, new Object()));
        assertEquals(429, blocked.getStatus());
    }

    @Test
    void authenticatedModeSeparatesUsersFromAnonymousIp() throws Exception {
        TokenBucketRateLimitInterceptor interceptor = interceptor(
                unavailableCache(),
                TokenBucketRateLimitInterceptor.SubjectMode.AUTHENTICATED_OR_IP,
                1);

        assertTrue(interceptor.preHandle(request("192.0.2.10"), response(), new Object()));
        assertFalse(interceptor.preHandle(request("192.0.2.10"), response(), new Object()));

        CurrentUserContext.bind(42, 2);
        assertTrue(interceptor.preHandle(request("192.0.2.10"), response(), new Object()));
    }

    @Test
    void ignoresCorsPreflight() throws Exception {
        TokenBucketRateLimitInterceptor interceptor = interceptor(
                unavailableCache(),
                TokenBucketRateLimitInterceptor.SubjectMode.IP_ONLY,
                1);
        MockHttpServletRequest preflight = request("192.0.2.10");
        preflight.setMethod("OPTIONS");

        assertTrue(interceptor.preHandle(preflight, response(), new Object()));
        assertTrue(interceptor.preHandle(request("192.0.2.10"), response(), new Object()));
    }

    private TokenBucketRateLimitInterceptor interceptor(
            CacheService cacheService,
            TokenBucketRateLimitInterceptor.SubjectMode subjectMode,
            int capacity) {
        return new TokenBucketRateLimitInterceptor(
                new ClientIpResolver(false, ""),
                cacheService,
                new ObjectMapper(),
                "test",
                subjectMode,
                capacity,
                Duration.ofMinutes(1));
    }

    private CacheService unavailableCache() {
        CacheService cacheService = mock(CacheService.class);
        when(cacheService.tryConsumeToken(anyString(), anyInt(), any()))
                .thenReturn(Optional.empty());
        return cacheService;
    }

    private MockHttpServletRequest request(String remoteAddress) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr(remoteAddress);
        return request;
    }

    private MockHttpServletResponse response() {
        return new MockHttpServletResponse();
    }
}
