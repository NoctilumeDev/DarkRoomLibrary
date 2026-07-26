package org.darkroomlibrary.service;

import org.darkroomlibrary.web.response.ApiResponse;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 罚款服务
 */
public interface FineService {

    /**
     * 计算逾期罚款
     * @param dueDate 应还日期
     * @param returnTime 实际归还时间
     * @return 罚款金额
     */
    BigDecimal calculateFine(LocalDateTime dueDate, LocalDateTime returnTime);

    /**
     * 逾期用户列表
     */
    ApiResponse<List<Map<String, Object>>> overdueUsers();

}
