package org.darkroomlibrary.service;

import org.darkroomlibrary.context.CurrentUserContext;
import org.darkroomlibrary.domain.model.User;
import org.darkroomlibrary.domain.type.AccountStatus;
import org.darkroomlibrary.mapper.UserMapper;
import org.springframework.stereotype.Component;

import jakarta.annotation.Resource;
import java.util.Objects;

/**
 * Central policy for reader-generated public content.
 */
@Component
public class ContentPostingPolicy {

    @Resource
    private UserMapper userMapper;

    public String currentUserRejectionReason() {
        User user = lockCurrentUser();
        return postingRejectionReason(user);
    }

    public String postingRejectionReason(User user) {
        String accountError = accountRejectionReason(user);
        if (accountError != null) {
            return accountError;
        }
        if (Boolean.TRUE.equals(user.getIsWord())) {
            return "当前账号已被禁言，暂不能发布或修改内容";
        }
        return null;
    }

    public String currentUserAccountRejectionReason() {
        return accountRejectionReason(lockCurrentUser());
    }

    private User lockCurrentUser() {
        Integer userId = CurrentUserContext.userId();
        if (userId == null) {
            return null;
        }
        return userMapper.findByIdForUpdate(userId);
    }

    private String accountRejectionReason(User user) {
        if (CurrentUserContext.userId() == null) {
            return "身份认证失败，请先登录";
        }
        if (user == null) {
            return "当前用户不存在或已被删除";
        }
        if (!Objects.equals(user.getAccountStatus(), AccountStatus.NORMAL.code())
                || Boolean.TRUE.equals(user.getIsLogin())) {
            return "当前账号状态不允许执行此操作";
        }
        if (!Objects.equals(user.getUserRole(), CurrentUserContext.roleCode())) {
            return "账号权限已变化，请刷新后重试";
        }
        return null;
    }
}
