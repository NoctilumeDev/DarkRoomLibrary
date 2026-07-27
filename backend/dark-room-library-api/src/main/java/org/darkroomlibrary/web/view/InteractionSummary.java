package org.darkroomlibrary.web.view;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Aggregated interaction counts for one business record.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class InteractionSummary {

    private Integer reviewId;
    private Integer totalCount;
    private Integer viewerCount;
}
