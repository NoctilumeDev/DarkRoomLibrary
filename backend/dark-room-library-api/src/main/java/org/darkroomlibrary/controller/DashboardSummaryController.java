package org.darkroomlibrary.controller;

import org.darkroomlibrary.aop.RequireRole;
import org.darkroomlibrary.web.response.ApiResponse;
import org.darkroomlibrary.web.view.MetricPoint;
import org.darkroomlibrary.service.DashboardSummaryService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.annotation.Resource;
import java.util.List;

@RestController
@RequestMapping("/views")
public class DashboardSummaryController {

    @Resource
    private DashboardSummaryService dashboardSummaryService;

    @RequireRole
    @GetMapping("/staticControls")
    public ApiResponse<List<MetricPoint>> staticControls() {
        return dashboardSummaryService.staticControls();
    }
}
