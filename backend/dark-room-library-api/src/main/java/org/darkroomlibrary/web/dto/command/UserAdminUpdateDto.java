package org.darkroomlibrary.web.dto.command;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 后台用户信息修改DTO（白名单字段，防止越权修改）
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class UserAdminUpdateDto {

    /**
     * 用户ID
     */
    @NotNull(message = "用户ID不能为空")
    private Integer id;

    /**
     * 用户账号
     */
    @Pattern(regexp = "^[a-zA-Z0-9_]{4,32}$", message = "账号为4-32位字母数字下划线")
    private String userAccount;

    /**
     * 用户昵称
     */
    @Size(min = 2, max = 20, message = "用户名长度2-20个字符")
    private String userName;

    /**
     * 用户头像
     */
    @Size(max = 500, message = "头像地址不能超过500个字符")
    private String userAvatar;

    /**
     * 用户邮箱
     */
    @Email(message = "邮箱格式不正确")
    @Size(max = 100, message = "邮箱不能超过100个字符")
    private String userEmail;

    /**
     * 超级管理员为其他用户重置的新密码；为空时不修改密码
     */
    @Size(min = 8, max = 20, message = "密码长度需要在8-20位之间")
    private String userPwd;

    /**
     * 登录状态（true=禁用）
     */
    private Boolean isLogin;

    /**
     * 禁言状态（true=禁言）
     */
    private Boolean isWord;

    /**
     * 用户角色
     */
    private Integer userRole;

    /**
     * 是否馆务协调员
     */
    private Boolean isCoordinatorAdmin;
}
