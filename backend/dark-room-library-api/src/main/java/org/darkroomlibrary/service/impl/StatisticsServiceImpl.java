package org.darkroomlibrary.service.impl;

import org.darkroomlibrary.infrastructure.cache.CacheService;
import org.darkroomlibrary.mapper.BookMapper;
import org.darkroomlibrary.mapper.BorrowRecordMapper;
import org.darkroomlibrary.mapper.UserMapper;
import org.darkroomlibrary.web.response.ApiResponse;
import org.darkroomlibrary.web.dto.query.BorrowRecordPageQuery;
import org.darkroomlibrary.domain.model.Book;
import org.darkroomlibrary.web.view.DailyCount;
import org.darkroomlibrary.service.FineService;
import org.darkroomlibrary.service.StatisticsService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import jakarta.annotation.Resource;
import java.time.Duration;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 统计服务实现（所有聚合操作都在数据库端完成，避免全量加载到内存）
 */
@Slf4j
@Service
public class StatisticsServiceImpl implements StatisticsService {

    private static final Duration STATISTICS_CACHE_TTL = Duration.ofMinutes(3);

    @Resource
    private BookMapper bookMapper;

    @Resource
    private BorrowRecordMapper borrowRecordMapper;

    @Resource
    private UserMapper userMapper;

    @Resource
    private FineService fineService;

    @Resource
    private CacheService cacheService;

    @Resource
    private ObjectMapper objectMapper;

    /**
     * 热门图书 — 数据库端 GROUP BY + LIMIT
     */
    @Override
    public ApiResponse<Map<String, Object>> hotBooks(Integer limit) {
        int actualLimit = limit == null ? 10 : Math.max(1, Math.min(limit, 100));
        String cacheKey = "cache:statistics:hot-books:" + actualLimit;
        Map<String, Object> cached = getCachedMap(cacheKey);
        if (cached != null) {
            return ApiResponse.success(cached);
        }
        List<Map<String, Object>> books = borrowRecordMapper.hotBookStats(actualLimit);
        Map<String, Object> result = new HashMap<>();
        result.put("books", books);
        result.put("total", books.size());
        putCachedMap(cacheKey, result);
        return ApiResponse.success(result);
    }

    /**
     * 低库存图书 — 数据库端 WHERE 过滤
     */
    @Override
    public ApiResponse<Map<String, Object>> lowStock(Integer threshold) {
        List<Book> books = bookMapper.queryLowStock(threshold);
        Map<String, Object> result = new HashMap<>();
        result.put("books", books);
        result.put("total", books.size());
        return ApiResponse.success(result);
    }

    /**
     * 总览 — 使用 queryCount，不加载具体数据
     */
    @Override
    public ApiResponse<Map<String, Object>> overview() {
        String cacheKey = "cache:statistics:overview";
        Map<String, Object> cached = getCachedMap(cacheKey);
        if (cached != null) {
            return ApiResponse.success(cached);
        }
        Map<String, Object> result = new HashMap<>();
        // queryCount 只执行 COUNT(*)，不加载数据
        result.put("totalBooks", bookMapper.queryCount(null));
        result.put("totalUsers", userMapper.queryCount(null));
        BorrowRecordPageQuery borrowDto = new BorrowRecordPageQuery();
        borrowDto.setStatus(false);
        result.put("activeBorrows", borrowRecordMapper.queryCount(borrowDto));
        borrowDto.setStatus(true);
        result.put("returnedBorrows", borrowRecordMapper.queryCount(borrowDto));
        putCachedMap(cacheKey, result);
        return ApiResponse.success(result);
    }

    /**
     * 月度借阅统计 — 仅查询指定年月，不加载全表
     */
    @Override
    public ApiResponse<List<Map<String, Object>>> monthlyBorrowStats(Integer year, Integer month) {
        if (year == null || year < 1970 || year > 2100 || month == null || month < 1 || month > 12) {
            return ApiResponse.error("统计年月不正确");
        }
        YearMonth yearMonth = YearMonth.of(year, month);
        List<DailyCount> dailyCounts = borrowRecordMapper.dailyBorrowStats(
                yearMonth.atDay(1).atStartOfDay(),
                yearMonth.plusMonths(1).atDay(1).atStartOfDay()
        );
        List<Map<String, Object>> result = new ArrayList<>();
        for (DailyCount dailyCount : dailyCounts) {
            Map<String, Object> map = new HashMap<>();
            map.put("day", String.format("%02d", dailyCount.getDay().getDayOfMonth()));
            map.put("count", dailyCount.getCount());
            result.add(map);
        }
        return ApiResponse.success(result);
    }

    /**
     * 逾期用户统计 — 复用 FineService 的数据库聚合
     */
    @Override
    public ApiResponse<List<Map<String, Object>>> overdueUserStats() {
        return fineService.overdueUsers();
    }

    /**
     * 馆藏分析 — 数据库端 GROUP BY 聚合
     */
    @Override
    public ApiResponse<Map<String, Object>> collectionAnalysis() {
        String cacheKey = "cache:statistics:collection-analysis";
        Map<String, Object> cached = getCachedMap(cacheKey);
        if (cached != null) {
            return ApiResponse.success(cached);
        }
        List<Map<String, Object>> stats = bookMapper.categoryStats();
        Map<String, Object> result = new HashMap<>();
        result.put("categories", stats);
        result.put("categoryCount", stats.size());
        putCachedMap(cacheKey, result);
        return ApiResponse.success(result);
    }

    private Map<String, Object> getCachedMap(String key) {
        try {
            String cached = cacheService.getString(key).orElse(null);
            if (cached != null) {
                return objectMapper.readValue(cached, new TypeReference<Map<String, Object>>() {});
            }
        } catch (Exception e) {
            log.warn("统计缓存读取失败，降级查询数据库: {}", e.getMessage());
        }
        return null;
    }

    private void putCachedMap(String key, Map<String, Object> value) {
        try {
            cacheService.setString(key, objectMapper.writeValueAsString(value), STATISTICS_CACHE_TTL);
        } catch (Exception e) {
            log.warn("统计缓存写入失败，忽略缓存: {}", e.getMessage());
        }
    }
}
