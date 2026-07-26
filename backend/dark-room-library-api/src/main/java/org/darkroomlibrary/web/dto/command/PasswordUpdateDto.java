package org.darkroomlibrary.web.dto.command;

import lombok.Data;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Data
public class PasswordUpdateDto {

    @NotBlank(message = "原始密码不能为空")
    @Size(max = 100, message = "原始密码不能超过100个字符")
    private String oldPwd;

    @NotBlank(message = "请输入新密码")
    @Size(min = 8, max = 20, message = "密码长度需要在8-20位之间")
    private String newPwd;

    @NotBlank(message = "请确认新密码")
    @Size(min = 8, max = 20, message = "密码长度需要在8-20位之间")
    private String againPwd;
}
