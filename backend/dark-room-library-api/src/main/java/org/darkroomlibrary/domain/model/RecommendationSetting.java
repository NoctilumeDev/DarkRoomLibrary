package org.darkroomlibrary.domain.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RecommendationSetting {
    private Integer userId;
    private Boolean enabled;
    private LocalDateTime updatedAt;
}
