package org.darkroomlibrary.service;

import org.darkroomlibrary.context.CurrentUserContext;
import org.darkroomlibrary.domain.model.User;
import org.darkroomlibrary.mapper.UserMapper;
import org.springframework.stereotype.Component;

import jakarta.annotation.Resource;

/**
 * Central policy for reader-generated public content.
 */
@Component
public class ContentPostingPolicy {

    @Resource
    private UserMapper userMapper;

    public String currentUserRejectionReason() {
        Integer userId = CurrentUserContext.userId();
        if (userId == null) {
            return "身份认证失败，请先登录";
        }
        User user = userMapper.getByActive(User.builder().id(userId).build());
        if (user == null) {
            return "当前用户不存在或已被删除";
        }
        if (Boolean.TRUE.equals(user.getIsWord())) {
            return "当前账号已被禁言，暂不能发布或修改内容";
        }
        return null;
    }
}
