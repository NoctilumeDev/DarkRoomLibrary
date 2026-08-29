package org.darkroomlibrary.web.dto.query;

import org.darkroomlibrary.web.dto.query.PageQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 用户查询DTO参数
 */
@EqualsAndHashCode(callSuper = true)
@Data
public class UserPageQuery extends PageQuery {
    /**
     * 用户的账号
     */
    private String userAccount;
    /**
     * 用户的名称
     */
    private String userName;
    /**
     * 用户的邮箱
     */
    private String userEmail;
    /**
     * 用户的角色
     */
    private Integer role;
    /**
     * 是否可以登录
     */
    private Boolean isLogin;
    /**
     * 是否被禁言
     */
    private Boolean isWord;
    /**
     * 是否馆务协调员
     */
    private Boolean isCoordinatorAdmin;
    /**
     * 账号状态
     */
    private Integer accountStatus;
}
