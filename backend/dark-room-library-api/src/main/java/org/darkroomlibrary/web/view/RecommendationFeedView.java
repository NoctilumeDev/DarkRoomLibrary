package org.darkroomlibrary.web.view;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class RecommendationFeedView {
    private String mode;
    private Boolean personalized;
    private Boolean enabled;
    private Integer signalCount;
    private LocalDateTime generatedAt;
    private String privacyNotice;
    private List<RecommendationItemView> items;
}
