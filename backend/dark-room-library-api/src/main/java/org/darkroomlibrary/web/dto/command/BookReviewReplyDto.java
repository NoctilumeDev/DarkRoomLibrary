package org.darkroomlibrary.web.dto.command;

import lombok.Data;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

@Data
public class BookReviewReplyDto {

    @NotBlank(message = "回复内容不能为空")
    @Size(max = 500, message = "回复内容不能超过500个字符")
    private String content;

    @Positive(message = "回复目标用户ID不合法")
    private Integer replyToUserId;
}
