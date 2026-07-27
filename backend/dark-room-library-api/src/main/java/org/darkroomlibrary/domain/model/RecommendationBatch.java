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
public class RecommendationBatch {
    private Long id;
    private Integer userId;
    private String mode;
    private String algorithmVersion;
    private Integer signalCount;
    private String sourceFingerprint;
    private LocalDateTime generatedAt;
    private LocalDateTime expiresAt;
}
