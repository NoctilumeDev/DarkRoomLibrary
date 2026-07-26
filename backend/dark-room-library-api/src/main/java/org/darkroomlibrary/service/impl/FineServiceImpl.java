package org.darkroomlibrary.service.impl;

import org.darkroomlibrary.mapper.BorrowRecordMapper;
import org.darkroomlibrary.web.response.ApiResponse;
import org.darkroomlibrary.service.FineService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import jakarta.annotation.Resource;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 罚款服务实现
 */
@Slf4j
@Service
public class FineServiceImpl implements FineService {

    @Value("${borrow.fine-per-day:0.5}")
    private double finePerDay;

    @Value("${borrow.fine-max:100}")
    private double fineMax;

    @Resource
    private BorrowRecordMapper borrowRecordMapper;

    @Override
    public BigDecimal calculateFine(LocalDateTime dueDate, LocalDateTime returnTime) {
        if (dueDate == null || returnTime == null) {
            return BigDecimal.ZERO;
        }
        if (!returnTime.isAfter(dueDate)) {
            return BigDecimal.ZERO;
        }
        long overdueDays = Duration.between(dueDate, returnTime).toDays();
        if (overdueDays <= 0) {
            return BigDecimal.ZERO;
        }
        BigDecimal fine = BigDecimal.valueOf(finePerDay).multiply(BigDecimal.valueOf(overdueDays))
                .setScale(2, RoundingMode.HALF_UP);
        BigDecimal max = BigDecimal.valueOf(fineMax);
        return fine.compareTo(max) > 0 ? max : fine;
    }

    @Override
    public ApiResponse<List<Map<String, Object>>> overdueUsers() {
        // 数据库端聚合：按用户分组统计逾期数量和总逾期天数
        List<Map<String, Object>> stats = borrowRecordMapper.overdueUserStats();
        // 补充计算罚款总额（逾期天数 × 每日罚金）
        for (Map<String, Object> row : stats) {
            Object daysObj = row.get("totalOverdueDays");
            long days = daysObj instanceof Number ? ((Number) daysObj).longValue() : 0;
            BigDecimal totalFine = BigDecimal.valueOf(finePerDay)
                    .multiply(BigDecimal.valueOf(days))
                    .setScale(2, RoundingMode.HALF_UP);
            row.put("totalFine", totalFine);
        }
        return ApiResponse.success(stats);
    }

}
