package org.darkroomlibrary.web.view;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Unread message count grouped by procurement order.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class OrderUnreadSummary {

    private Integer orderId;
    private Integer unreadCount;
}
