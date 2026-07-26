package org.darkroomlibrary.service;

import org.darkroomlibrary.web.response.ApiResponse;

import java.util.Map;

public interface AdminWorkflowService {

    ApiResponse<Map<String, Object>> auditStatus();

    ApiResponse<Map<String, Object>> backendFlow();
}
