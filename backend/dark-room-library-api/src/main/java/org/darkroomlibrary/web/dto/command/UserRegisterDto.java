package org.darkroomlibrary.web.dto.command;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class UserRegisterDto {
    /**
     * 用户名
     */
    @NotBlank(message = "用户名不能为空")
    @Size(min = 2, max = 20, message = "用户名长度2-20个字符")
    private String userName;

    /**
     * 账号
     */
    @NotBlank(message = "账号不能为空")
    @Pattern(regexp = "^[a-zA-Z0-9_]{4,32}$", message = "账号为4-32位字母数字下划线")
    private String userAccount;

    /**
     * 密码
     */
    @NotBlank(message = "密码不能为空")
    @Size(min = 8, max = 20, message = "密码长度需要在8-20位之间")
    private String userPwd;

    /**
     * 用户邮箱
     */
    @NotBlank(message = "邮箱不能为空")
    @Email(message = "邮箱格式不正确")
    @Size(max = 100, message = "邮箱不能超过100个字符")
    private String userEmail;

    /**
     * 用户头像
     */
    private String userAvatar;

    /**
     * 验证码
     */
    @Pattern(regexp = "^\\d{6}$", message = "邮箱验证码为6位数字")
    private String verificationCode;

    /**
     * 角色：不传默认读者
     */
    private Integer userRole;

    /**
     * 是否馆务协调员，仅超级管理员新增管理员时有效
     */
    private Boolean isCoordinatorAdmin;
}
