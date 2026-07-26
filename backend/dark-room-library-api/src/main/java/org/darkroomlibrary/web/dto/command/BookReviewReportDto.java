package org.darkroomlibrary.web.dto.command;

import lombok.Data;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Data
public class BookReviewReportDto {

    @NotBlank(message = "举报原因不能为空")
    @Size(max = 200, message = "举报原因不能超过200字")
    private String reason;
}
