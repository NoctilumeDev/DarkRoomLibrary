package org.darkroomlibrary.context;

import org.darkroomlibrary.domain.type.UserRole;

/**
 * Request-scoped identity populated by the JWT interceptor.
 */
public final class CurrentUserContext {

    private static final ThreadLocal<Identity> CURRENT = new ThreadLocal<>();

    private CurrentUserContext() {
    }

    public static void bind(Integer userId, Integer roleCode) {
        if (userId == null || roleCode == null) {
            clear();
            return;
        }
        CURRENT.set(new Identity(userId, roleCode));
    }

    public static Integer userId() {
        Identity identity = CURRENT.get();
        return identity == null ? null : identity.userId();
    }

    public static Integer roleCode() {
        Identity identity = CURRENT.get();
        return identity == null ? null : identity.roleCode();
    }

    public static boolean isAdministrator() {
        return UserRole.fromCode(roleCode())
                .map(UserRole::isAdministrator)
                .orElse(false);
    }

    public static void clear() {
        CURRENT.remove();
    }

    private record Identity(Integer userId, Integer roleCode) {
    }
}
