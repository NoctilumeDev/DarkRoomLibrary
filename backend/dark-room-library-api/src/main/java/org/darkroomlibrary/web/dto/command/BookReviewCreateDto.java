package org.darkroomlibrary.web.dto.command;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BookReviewCreateDto {

    @NotNull(message = "图书ID不能为空")
    private Integer bookId;

    @NotNull(message = "评分不能为空")
    @Min(value = 1, message = "评分必须在1-5之间")
    @Max(value = 5, message = "评分必须在1-5之间")
    private Integer rating;

    @NotBlank(message = "评价内容不能为空")
    @Size(max = 1000, message = "评价内容不能超过1000个字符")
    private String content;
}
