package org.darkroomlibrary.web.dto.query;

import org.darkroomlibrary.web.dto.query.PageQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 留言板查询DTO
 */
@EqualsAndHashCode(callSuper = true)
@Data
public class MessageBoardPageQuery extends PageQuery {
    /**
     * 用户ID
     */
    private Integer userId;
    /**
     * 内容关键词
     */
    private String content;
}