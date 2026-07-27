package org.darkroomlibrary.infrastructure.security;

import org.darkroomlibrary.domain.model.User;
import org.darkroomlibrary.domain.type.AccountStatus;
import org.darkroomlibrary.mapper.UserMapper;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.stereotype.Component;

import jakarta.annotation.Resource;
import java.util.Optional;

/**
 * Reads authorization state directly from MySQL so role and account changes take
 * effect on the next request across every application instance.
 */
@Component
public class UserAuthLookup {

    @Resource
    private UserMapper userMapper;

    public Optional<AuthUser> getActiveUser(Integer userId) {
        if (userId == null) {
            return Optional.empty();
        }
        User user = userMapper.getById(userId);
        if (user == null) {
            return Optional.empty();
        }
        return Optional.of(new AuthUser(
                user.getId(),
                user.getUserName(),
                user.getUserRole(),
                isDisabled(user)
        ));
    }

    private boolean isDisabled(User user) {
        return Boolean.TRUE.equals(user.getIsLogin())
                || AccountStatus.FROZEN.code().equals(user.getAccountStatus())
                || AccountStatus.CANCELLED.code().equals(user.getAccountStatus());
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AuthUser {
        private Integer id;
        private String userName;
        private Integer userRole;
        private Boolean disabled;
    }
}
