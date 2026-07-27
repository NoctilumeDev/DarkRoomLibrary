package org.darkroomlibrary.web.dto.command;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class RecommendationEventDto {
    @NotBlank(message = "推荐事件不能为空")
    @Pattern(regexp = "CLICK|DISMISS", message = "不支持的推荐事件")
    private String eventType;
}
