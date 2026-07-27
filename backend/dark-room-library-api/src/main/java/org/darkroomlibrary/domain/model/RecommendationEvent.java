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
public class RecommendationEvent {
    private Long id;
    private Integer userId;
    private Long itemId;
    private String eventType;
    private LocalDateTime createdAt;
}
