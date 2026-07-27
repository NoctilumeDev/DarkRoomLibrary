package org.darkroomlibrary.domain.recommendation;

import lombok.Data;

@Data
public class RecommendationFavoriteLink {
    private Integer userId;
    private Integer bookId;
}
