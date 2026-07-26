package org.darkroomlibrary.web.dto.command;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class UserLoginDto {
    /**
     * 账号
     */
    @NotBlank(message = "账号不能为空")
    private String userAccount;

    /**
     * 密码
     */
    @NotBlank(message = "密码不能为空")
    private String userPwd;

    /**
     * 登录验证码ID
     */
    @NotBlank(message = "验证码不能为空")
    private String captchaId;

    /**
     * 登录验证码答案
     */
    @NotNull(message = "验证码不能为空")
    private Integer captchaAnswer;
}
