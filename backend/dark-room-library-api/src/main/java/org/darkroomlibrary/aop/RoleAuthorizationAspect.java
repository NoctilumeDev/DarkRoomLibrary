package org.darkroomlibrary.aop;

import org.darkroomlibrary.context.CurrentUserContext;
import org.darkroomlibrary.web.response.ApiResponse;
import org.darkroomlibrary.domain.type.UserRole;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Optional;

/**
 * Enforces endpoint role requirements after the JWT interceptor binds identity.
 */
@Aspect
@Component
public class RoleAuthorizationAspect {

    @Around("@annotation(requirement)")
    public Object authorize(ProceedingJoinPoint joinPoint, RequireRole requirement) throws Throwable {
        if (CurrentUserContext.userId() == null) {
            return ApiResponse.error("身份认证失败，请先登录");
        }

        Optional<UserRole> currentRole = UserRole.fromCode(CurrentUserContext.roleCode());
        if (currentRole.isEmpty()) {
            return ApiResponse.error("身份认证失败，请先登录");
        }

        UserRole role = currentRole.get();
        UserRole[] allowed = requirement.value();
        boolean authenticatedOnly = allowed.length == 0;
        boolean inheritsAdministratorAccess = role == UserRole.SUPER_ADMIN
                && Arrays.asList(allowed).contains(UserRole.ADMIN);
        boolean authorized = authenticatedOnly
                || Arrays.asList(allowed).contains(role)
                || inheritsAdministratorAccess;

        if (authorized) {
            return joinPoint.proceed();
        }
        return ApiResponse.error("无操作权限");
    }
}
