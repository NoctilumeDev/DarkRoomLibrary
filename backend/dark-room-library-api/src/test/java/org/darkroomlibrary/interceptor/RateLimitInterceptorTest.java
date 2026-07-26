package org.darkroomlibrary.interceptor;

import org.darkroomlibrary.context.CurrentUserContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RateLimitInterceptorTest {

    @AfterEach
    void clearThreadContext() {
        CurrentUserContext.clear();
    }

    @Test
    void ignoresSpoofedForwardedHeaderByDefault() throws Exception {
        RateLimitInterceptor interceptor = interceptor(false, 60, 300);

        for (int i = 0; i < 60; i++) {
            assertTrue(interceptor.preHandle(request("10.0.0.1", "192.0.2." + i),
                    new MockHttpServletResponse(), new Object()));
        }

        MockHttpServletResponse response = new MockHttpServletResponse();
        assertFalse(interceptor.preHandle(request("10.0.0.1", "198.51.100.1"), response, new Object()));
        assertEquals(429, response.getStatus());
        assertTrue(response.getContentAsString().contains("\"code\":429"));
    }

    @Test
    void acceptsForwardedHeaderOnlyWhenExplicitlyTrusted() throws Exception {
        RateLimitInterceptor interceptor = interceptor(true, 60, 300);

        for (int i = 0; i < 61; i++) {
            assertTrue(interceptor.preHandle(request("10.0.0.1", "192.0.2." + i),
                    new MockHttpServletResponse(), new Object()));
        }
    }

    @Test
    void keepsAnonymousAndAuthenticatedBudgetsSeparate() throws Exception {
        RateLimitInterceptor interceptor = interceptor(false, 2, 3);
        MockHttpServletRequest request = request("10.0.0.1", null);

        assertTrue(interceptor.preHandle(request, new MockHttpServletResponse(), new Object()));
        assertTrue(interceptor.preHandle(request, new MockHttpServletResponse(), new Object()));
        assertFalse(interceptor.preHandle(request, new MockHttpServletResponse(), new Object()));

        CurrentUserContext.bind(42, 2);
        assertTrue(interceptor.preHandle(request, new MockHttpServletResponse(), new Object()));
        assertTrue(interceptor.preHandle(request, new MockHttpServletResponse(), new Object()));
        assertTrue(interceptor.preHandle(request, new MockHttpServletResponse(), new Object()));
        assertFalse(interceptor.preHandle(request, new MockHttpServletResponse(), new Object()));
    }

    @Test
    void doesNotCountCorsPreflightRequests() throws Exception {
        RateLimitInterceptor interceptor = interceptor(false, 1, 1);
        MockHttpServletRequest preflight = request("10.0.0.1", null);
        preflight.setMethod("OPTIONS");

        assertTrue(interceptor.preHandle(preflight, new MockHttpServletResponse(), new Object()));
        assertTrue(interceptor.preHandle(request("10.0.0.1", null),
                new MockHttpServletResponse(), new Object()));
    }

    private RateLimitInterceptor interceptor(boolean trustForwardedHeaders,
                                             int anonymousLimit,
                                             int authenticatedLimit) {
        return new RateLimitInterceptor(
                trustForwardedHeaders,
                anonymousLimit,
                authenticatedLimit,
                new ObjectMapper());
    }

    private MockHttpServletRequest request(String remoteAddress, String forwardedAddress) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr(remoteAddress);
        if (forwardedAddress != null) {
            request.addHeader("X-Forwarded-For", forwardedAddress);
        }
        return request;
    }
}
