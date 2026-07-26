package org.darkroomlibrary.controller;

import org.darkroomlibrary.aop.NormalizePageQuery;
import org.darkroomlibrary.aop.RequireRole;
import org.darkroomlibrary.domain.type.UserRole;
import org.darkroomlibrary.web.response.ApiResponse;
import org.darkroomlibrary.web.dto.query.OperationLogPageQuery;
import org.darkroomlibrary.domain.model.OperationLog;
import org.darkroomlibrary.service.OperationLogService;
import org.springframework.web.bind.annotation.*;

import jakarta.annotation.Resource;
import java.util.List;

/**
 * 操作日志控制器
 */
@RestController
@RequestMapping("/operationLog")
public class OperationLogController {

    @Resource
    private OperationLogService operationLogService;

    /**
     * 分页查询操作日志
     */
    @NormalizePageQuery
    @RequireRole(UserRole.ADMIN)
    @PostMapping("/query")
    public ApiResponse<List<OperationLog>> query(@RequestBody OperationLogPageQuery dto) {
        return operationLogService.query(dto);
    }
}