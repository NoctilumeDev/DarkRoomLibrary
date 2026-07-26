package org.darkroomlibrary.controller;

import org.darkroomlibrary.aop.RequireRole;
import org.darkroomlibrary.domain.type.UserRole;
import org.darkroomlibrary.web.response.ApiResponse;
import org.darkroomlibrary.service.AdminWorkflowService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.annotation.Resource;
import java.util.Map;

@RestController
@RequestMapping("/adminWorkflow")
public class AdminWorkflowController {

    @Resource
    private AdminWorkflowService adminWorkflowService;

    @RequireRole({UserRole.ADMIN, UserRole.SUPER_ADMIN})
    @GetMapping("/auditStatus")
    public ApiResponse<Map<String, Object>> auditStatus() {
        return adminWorkflowService.auditStatus();
    }

    @RequireRole({UserRole.ADMIN, UserRole.SUPER_ADMIN})
    @GetMapping("/backendFlow")
    public ApiResponse<Map<String, Object>> backendFlow() {
        return adminWorkflowService.backendFlow();
    }
}
