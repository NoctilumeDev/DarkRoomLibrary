package org.darkroomlibrary.domain.recommendation;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class RecommendationUserSignal {
    private Integer bookId;
    private Integer favoriteCount;
    private Integer borrowCount;
    private Integer activeBorrowCount;
    private Integer reviewCount;
    private Double averageRating;
    private LocalDateTime latestInteractionTime;
}
