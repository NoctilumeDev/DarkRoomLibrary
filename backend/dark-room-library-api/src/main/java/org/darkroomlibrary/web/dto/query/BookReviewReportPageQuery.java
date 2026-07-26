package org.darkroomlibrary.web.dto.query;

import org.darkroomlibrary.web.dto.query.PageQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 图书评价举报审核查询DTO
 */
@EqualsAndHashCode(callSuper = true)
@Data
public class BookReviewReportPageQuery extends PageQuery {
    /**
     * 举报状态：0=待处理，1=已处理，2=忽略
     */
    private Integer status;
    /**
     * 图书名称
     */
    private String bookName;
    /**
     * 评价内容
     */
    private String reviewContent;
}
