package org.darkroomlibrary.web.dto.query;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Shared pagination and optional time-range input for list endpoints.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class PageQuery {
    private Integer current;
    private Integer size;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
}
