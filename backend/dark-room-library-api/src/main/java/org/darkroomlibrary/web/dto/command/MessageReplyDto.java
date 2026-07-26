package org.darkroomlibrary.web.dto.command;

import lombok.Data;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

@Data
public class MessageReplyDto {

    @NotNull(message = "留言ID不能为空")
    @Positive(message = "留言ID不合法")
    private Integer id;

    @NotBlank(message = "回复内容不能为空")
    @Size(max = 1000, message = "回复内容不能超过1000个字符")
    private String reply;
}
