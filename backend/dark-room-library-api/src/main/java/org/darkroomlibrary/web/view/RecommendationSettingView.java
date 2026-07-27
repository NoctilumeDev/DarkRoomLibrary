package org.darkroomlibrary.web.view;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class RecommendationSettingView {
    private Boolean enabled;
    private String dataScope;
    private String clearEffect;
}
