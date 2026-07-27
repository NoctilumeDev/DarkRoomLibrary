package org.darkroomlibrary.service.impl;

import org.darkroomlibrary.mapper.OperationLogMapper;
import org.darkroomlibrary.web.response.ApiResponse;
import org.darkroomlibrary.web.response.PageResponse;
import org.darkroomlibrary.web.dto.query.OperationLogPageQuery;
import org.darkroomlibrary.domain.model.OperationLog;
import org.darkroomlibrary.service.OperationLogService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import jakarta.annotation.Resource;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * 操作日志服务实现
 */
@Service
@Slf4j
public class OperationLogServiceImpl implements OperationLogService {

    @Resource
    private OperationLogMapper operationLogMapper;

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(OperationLog operationLog) {
        if (operationLog.getEventKey() == null || operationLog.getEventKey().isBlank()) {
            operationLog.setEventKey(UUID.randomUUID().toString());
        }
        operationLog.setCreateTime(LocalDateTime.now());
        if (operationLogMapper.insert(operationLog) != 1) {
            throw new IllegalStateException("操作日志写入失败");
        }
    }

    @Override
    public ApiResponse<List<OperationLog>> query(OperationLogPageQuery dto) {
        List<OperationLog> list = operationLogMapper.query(dto);
        Integer total = operationLogMapper.queryCount(dto);
        return PageResponse.success(list, total);
    }

}
