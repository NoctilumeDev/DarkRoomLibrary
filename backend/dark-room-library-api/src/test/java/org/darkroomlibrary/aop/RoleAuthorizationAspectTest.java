package org.darkroomlibrary.aop;

import org.aspectj.lang.ProceedingJoinPoint;
import org.darkroomlibrary.context.CurrentUserContext;
import org.darkroomlibrary.domain.type.UserRole;
import org.darkroomlibrary.web.response.ApiResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RoleAuthorizationAspectTest {

    private final RoleAuthorizationAspect aspect = new RoleAuthorizationAspect();

    @AfterEach
    void clearContext() {
        CurrentUserContext.clear();
    }

    @Test
    void superAdminDoesNotInheritReaderOnlyActions() throws Throwable {
        CurrentUserContext.bind(1, UserRole.SUPER_ADMIN.code());
        ProceedingJoinPoint joinPoint = mock(ProceedingJoinPoint.class);

        Object result = aspect.authorize(joinPoint, requirement("readerOnly"));

        assertEquals(400, ((ApiResponse<?>) result).getCode());
        verify(joinPoint, never()).proceed();
    }

    @Test
    void superAdminInheritsAdministratorActions() throws Throwable {
        CurrentUserContext.bind(1, UserRole.SUPER_ADMIN.code());
        ProceedingJoinPoint joinPoint = mock(ProceedingJoinPoint.class);
        Object expected = new Object();
        when(joinPoint.proceed()).thenReturn(expected);

        Object result = aspect.authorize(joinPoint, requirement("adminOnly"));

        assertSame(expected, result);
        verify(joinPoint).proceed();
    }

    @Test
    void superAdminDoesNotInheritAcquisitionsOnlyActions() throws Throwable {
        CurrentUserContext.bind(1, UserRole.SUPER_ADMIN.code());
        ProceedingJoinPoint joinPoint = mock(ProceedingJoinPoint.class);

        Object result = aspect.authorize(joinPoint, requirement("acquisitionsOnly"));

        assertEquals(400, ((ApiResponse<?>) result).getCode());
        verify(joinPoint, never()).proceed();
    }

    private RequireRole requirement(String methodName) throws NoSuchMethodException {
        Method method = SecuredMethods.class.getDeclaredMethod(methodName);
        return method.getAnnotation(RequireRole.class);
    }

    private static class SecuredMethods {
        @RequireRole(UserRole.READER)
        void readerOnly() {
        }

        @RequireRole(UserRole.ADMIN)
        void adminOnly() {
        }

        @RequireRole(UserRole.ACQUISITIONS)
        void acquisitionsOnly() {
        }
    }
}
