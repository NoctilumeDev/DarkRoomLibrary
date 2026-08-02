package org.darkroomlibrary.interceptor;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Claims;
import org.darkroomlibrary.context.CurrentUserContext;
import org.darkroomlibrary.infrastructure.security.UserAuthLookup;
import org.darkroomlibrary.utils.JwtUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class JwtInterceptorTest {

    @AfterEach
    void clearContext() {
        CurrentUserContext.clear();
    }

    @Test
    void rejectsTokenWhenAuthenticationVersionHasChanged() throws Exception {
        JwtUtil jwtUtil = mock(JwtUtil.class);
        Claims claims = mock(Claims.class);
        UserAuthLookup lookup = mock(UserAuthLookup.class);
        when(jwtUtil.fromToken("stale")).thenReturn(claims);
        when(claims.get("id", Integer.class)).thenReturn(7);
        when(claims.get("authVersion", Integer.class)).thenReturn(1);
        when(lookup.getActiveUser(7)).thenReturn(Optional.of(
                new UserAuthLookup.AuthUser(7, "reader", 2, 2, false)));

        JwtInterceptor interceptor = new JwtInterceptor(
                "/api/test", lookup, new ObjectMapper(), jwtUtil);
        MockHttpServletRequest request = protectedRequest("stale");
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertFalse(interceptor.preHandle(request, response, new Object()));
        assertEquals(401, response.getStatus());
    }

    @Test
    void bindsFreshDatabaseRoleForCurrentAuthenticationVersion() throws Exception {
        JwtUtil jwtUtil = mock(JwtUtil.class);
        Claims claims = mock(Claims.class);
        UserAuthLookup lookup = mock(UserAuthLookup.class);
        when(jwtUtil.fromToken("fresh")).thenReturn(claims);
        when(claims.get("id", Integer.class)).thenReturn(7);
        when(claims.get("authVersion", Integer.class)).thenReturn(3);
        when(lookup.getActiveUser(7)).thenReturn(Optional.of(
                new UserAuthLookup.AuthUser(7, "buyer", 3, 3, false)));

        JwtInterceptor interceptor = new JwtInterceptor(
                "/api/test", lookup, new ObjectMapper(), jwtUtil);

        assertTrue(interceptor.preHandle(
                protectedRequest("fresh"), new MockHttpServletResponse(), new Object()));
        assertEquals(3, CurrentUserContext.roleCode());
    }

    private MockHttpServletRequest protectedRequest(String token) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/api/test/user/auth");
        request.addHeader("Authorization", "Bearer " + token);
        return request;
    }
}
