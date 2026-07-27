package org.darkroomlibrary.web.dto.command;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class RecommendationSettingUpdateDto {
    @NotNull(message = "推荐开关不能为空")
    private Boolean enabled;
}
