package org.darkroomlibrary.service;

import org.darkroomlibrary.web.response.ApiResponse;

import java.util.List;
import java.util.Map;

/**
 * 统计服务
 */
public interface StatisticsService {
    /**
     * 热门图书
     */
    ApiResponse<Map<String, Object>> hotBooks(Integer limit);

    /**
     * 低库存预警
     */
    ApiResponse<Map<String, Object>> lowStock(Integer threshold);

    /**
     * 数据概览
     */
    ApiResponse<Map<String, Object>> overview();

    /**
     * 月度借阅统计
     */
    ApiResponse<List<Map<String, Object>>> monthlyBorrowStats(Integer year, Integer month);

    /**
     * 逾期用户统计
     */
    ApiResponse<List<Map<String, Object>>> overdueUserStats();

    /**
     * 馆藏分析
     */
    ApiResponse<Map<String, Object>> collectionAnalysis();
}