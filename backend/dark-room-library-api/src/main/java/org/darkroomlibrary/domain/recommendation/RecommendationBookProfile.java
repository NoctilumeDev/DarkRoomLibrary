package org.darkroomlibrary.domain.recommendation;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class RecommendationBookProfile {
    private Integer id;
    private Integer version;
    private String name;
    private String author;
    private String publisher;
    private String category;
    private String cover;
    private String description;
    private Integer totalCount;
    private Integer availableCount;
    private LocalDateTime createTime;
    private Integer favoriteCount;
    private Integer borrowCount;
    private Integer reviewCount;
    private Double averageRating;
}
