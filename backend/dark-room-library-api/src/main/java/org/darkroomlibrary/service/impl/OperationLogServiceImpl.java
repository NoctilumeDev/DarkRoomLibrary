package org.darkroomlibrary.service.impl;

import org.darkroomlibrary.infrastructure.event.DomainEventPublisher;
import org.darkroomlibrary.mapper.OperationLogMapper;
import org.darkroomlibrary.web.response.ApiResponse;
import org.darkroomlibrary.web.response.PageResponse;
import org.darkroomlibrary.web.dto.query.OperationLogPageQuery;
import org.darkroomlibrary.domain.model.OperationLog;
import org.darkroomlibrary.service.OperationLogService;
import org.springframework.stereotype.Service;

import jakarta.annotation.Resource;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 操作日志服务实现
 */
@Service
public class OperationLogServiceImpl implements OperationLogService {

    @Resource
    private OperationLogMapper operationLogMapper;

    @Resource
    private DomainEventPublisher domainEventPublisher;

    @org.springframework.beans.factory.annotation.Value("${middleware.rabbit.operation-log-routing-key:operation.log}")
    private String operationLogRoutingKey;

    @Override
    public void record(OperationLog operationLog) {
        operationLog.setCreateTime(LocalDateTime.now());
        if (domainEventPublisher.publish(operationLogRoutingKey, operationLog)) {
            return;
        }
        operationLogMapper.insert(operationLog);
    }

    @Override
    public ApiResponse<List<OperationLog>> query(OperationLogPageQuery dto) {
        List<OperationLog> list = operationLogMapper.query(dto);
        Integer total = operationLogMapper.queryCount(dto);
        return PageResponse.success(list, total);
    }
}
