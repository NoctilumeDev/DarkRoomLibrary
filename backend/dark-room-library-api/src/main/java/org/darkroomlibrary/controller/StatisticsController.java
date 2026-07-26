package org.darkroomlibrary.controller;

import org.darkroomlibrary.aop.RequireRole;
import org.darkroomlibrary.domain.type.UserRole;
import org.darkroomlibrary.web.response.ApiResponse;
import org.darkroomlibrary.service.StatisticsService;
import org.springframework.web.bind.annotation.*;

import jakarta.annotation.Resource;
import java.util.List;
import java.util.Map;

/**
 * 统计控制器
 */
@RestController
@RequestMapping("/statistics")
public class StatisticsController {

    @Resource
    private StatisticsService statisticsService;

    @RequireRole({UserRole.ADMIN, UserRole.SUPER_ADMIN})
    @GetMapping("/hotBooks")
    public ApiResponse<Map<String, Object>> hotBooks(@RequestParam(defaultValue = "10") Integer limit) {
        return statisticsService.hotBooks(limit);
    }

    @RequireRole({UserRole.ADMIN, UserRole.SUPER_ADMIN, UserRole.ACQUISITIONS})
    @GetMapping("/lowStock")
    public ApiResponse<Map<String, Object>> lowStock(@RequestParam(defaultValue = "3") Integer threshold) {
        return statisticsService.lowStock(threshold);
    }

    @RequireRole({UserRole.ADMIN, UserRole.SUPER_ADMIN})
    @GetMapping("/overview")
    public ApiResponse<Map<String, Object>> overview() {
        return statisticsService.overview();
    }

    @RequireRole({UserRole.ADMIN, UserRole.SUPER_ADMIN})
    @GetMapping("/monthlyBorrow/{year}/{month}")
    public ApiResponse<List<Map<String, Object>>> monthlyBorrow(@PathVariable Integer year, @PathVariable Integer month) {
        return statisticsService.monthlyBorrowStats(year, month);
    }

    @RequireRole({UserRole.ADMIN, UserRole.SUPER_ADMIN})
    @GetMapping("/overdueUsers")
    public ApiResponse<List<Map<String, Object>>> overdueUsers() {
        return statisticsService.overdueUserStats();
    }

    @RequireRole({UserRole.ADMIN, UserRole.SUPER_ADMIN})
    @GetMapping("/collectionAnalysis")
    public ApiResponse<Map<String, Object>> collectionAnalysis() {
        return statisticsService.collectionAnalysis();
    }
}
