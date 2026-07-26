package org.darkroomlibrary.service;

import org.darkroomlibrary.web.response.ApiResponse;
import org.darkroomlibrary.web.dto.query.OperationLogPageQuery;
import org.darkroomlibrary.domain.model.OperationLog;

import java.util.List;

/**
 * 操作日志服务接口
 */
public interface OperationLogService {

    void record(OperationLog operationLog);

    ApiResponse<List<OperationLog>> query(OperationLogPageQuery dto);
}