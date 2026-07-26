package org.darkroomlibrary.web.dto.command;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class UserUpdateDto {
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
}
