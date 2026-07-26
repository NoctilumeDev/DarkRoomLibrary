package org.darkroomlibrary.web.dto.query;

import org.darkroomlibrary.web.dto.query.PageQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 操作日志查询DTO
 */
@EqualsAndHashCode(callSuper = true)
@Data
public class OperationLogPageQuery extends PageQuery {
    /**
     * 用户ID
     */
    private Integer userId;
    /**
     * 操作类型
     */
    private String operation;
}