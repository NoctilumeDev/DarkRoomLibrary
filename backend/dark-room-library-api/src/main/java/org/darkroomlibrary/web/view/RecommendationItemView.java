package org.darkroomlibrary.web.view;

import lombok.Data;

@Data
public class RecommendationItemView {
    private Long itemId;
    private Integer bookId;
    private String name;
    private String author;
    private String category;
    private String cover;
    private String description;
    private Integer availableCount;
    private String sourceType;
    private String reason;
}
