package org.darkroomlibrary.web.dto.query;

import org.darkroomlibrary.web.dto.query.PageQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 图书预约查询DTO
 */
@EqualsAndHashCode(callSuper = true)
@Data
public class BookReservationPageQuery extends PageQuery {
    /**
     * 用户ID
     */
    private Integer userId;
    /**
     * 图书ID
     */
    private Integer bookId;
    /**
     * 状态(0:预约中, 1:已借阅, 2:已取消)
     */
    private Integer status;
}