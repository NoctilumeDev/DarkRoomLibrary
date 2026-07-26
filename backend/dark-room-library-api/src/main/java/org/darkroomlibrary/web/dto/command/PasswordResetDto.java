package org.darkroomlibrary.web.dto.command;

import lombok.Data;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

@Data
public class PasswordResetDto {

    @NotBlank(message = "请输入账号")
    @Pattern(regexp = "^[a-zA-Z0-9_]{4,32}$", message = "账号为4-32位字母数字下划线")
    private String account;

    @NotBlank(message = "请输入邮箱")
    @Email(message = "邮箱格式不正确")
    @Size(max = 100, message = "邮箱不能超过100个字符")
    private String email;

    @NotBlank(message = "请输入验证码")
    @Size(max = 20, message = "验证码不能超过20个字符")
    private String code;

    @NotBlank(message = "请输入新密码")
    @Size(min = 8, max = 20, message = "密码长度需要在8-20位之间")
    private String newPwd;
}
