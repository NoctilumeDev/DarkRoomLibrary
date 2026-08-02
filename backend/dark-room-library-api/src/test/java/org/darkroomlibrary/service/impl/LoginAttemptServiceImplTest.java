package org.darkroomlibrary.service.impl;

import org.darkroomlibrary.infrastructure.security.ClientIpResolver;
import org.darkroomlibrary.infrastructure.security.LoginAttemptStore;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class LoginAttemptServiceImplTest {

    @AfterEach
    void clearRequest() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void scopesFailuresToAccountAndRequestSource() {
        LoginAttemptStore store = mock(LoginAttemptStore.class);
        LoginAttemptServiceImpl service = new LoginAttemptServiceImpl();
        ReflectionTestUtils.setField(service, "maxFailAttempts", 5);
        ReflectionTestUtils.setField(service, "lockDurationMinutes", 30);
        ReflectionTestUtils.setField(service, "loginAttemptStore", store);
        ReflectionTestUtils.setField(service, "clientIpResolver", new ClientIpResolver(false, ""));

        bindRequest("192.0.2.10");
        service.loginFailed(" Reader ");
        bindRequest("192.0.2.11");
        service.loginFailed("reader");

        verify(store).loginFailed("reader|192.0.2.10", 5, 30);
        verify(store).loginFailed("reader|192.0.2.11", 5, 30);
    }

    private void bindRequest(String address) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr(address);
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    }
}
