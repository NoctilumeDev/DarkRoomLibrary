package org.darkroomlibrary.domain.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RecommendationItem {
    private Long id;
    private Long batchId;
    private Integer userId;
    private Integer bookId;
    private Integer rankNo;
    private BigDecimal totalScore;
    private BigDecimal contentScore;
    private BigDecimal collaborativeScore;
    private BigDecimal qualityScore;
    private BigDecimal explorationScore;
    private String sourceType;
    private String reason;
}
