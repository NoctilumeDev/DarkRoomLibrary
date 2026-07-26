package org.darkroomlibrary.web.dto.command;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BookReviewUpdateDto {

    @NotNull(message = "评价ID不能为空")
    private Integer id;

    @Min(value = 1, message = "评分必须在1-5之间")
    @Max(value = 5, message = "评分必须在1-5之间")
    private Integer rating;

    @Size(min = 1, max = 1000, message = "评价内容长度必须为1-1000个字符")
    private String content;
}
