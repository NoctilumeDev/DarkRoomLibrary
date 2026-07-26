package org.darkroomlibrary.web.dto.query;

import org.darkroomlibrary.web.dto.query.PageQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 借阅记录查询DTO
 */
@EqualsAndHashCode(callSuper = true)
@Data
public class BorrowRecordPageQuery extends PageQuery {
    /**
     * 用户ID
     */
    private Integer userId;
    /**
     * 图书ID
     */
    private Integer bookId;
    /**
     * 借阅状态：false=借阅中，true=已归还
     */
    private Boolean status;
    /**
     * 是否逾期(null:全部, true:逾期, false:未逾期)
     */
    private Boolean overdue;
    /**
     * 图书名称(模糊查询)
     */
    private String bookName;
    /**
     * 借阅人(模糊查询)
     */
    private String userName;
}
