package org.darkroomlibrary.mapper;

import org.darkroomlibrary.web.dto.query.BorrowRecordPageQuery;
import org.darkroomlibrary.domain.model.BorrowRecord;
import org.darkroomlibrary.web.view.BorrowRecordView;
import org.darkroomlibrary.web.view.DailyCount;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Mapper
public interface BorrowRecordMapper extends BaseMapper<BorrowRecord> {

    default BorrowRecord getById(Integer id) { return selectById(id); }

    BorrowRecord findByIdForUpdate(@Param("id") Integer id);

    int insertIfNoActiveBorrow(BorrowRecord record);

    int updateReturnStatus(@Param("id") Integer id,
                           @Param("returnTime") LocalDateTime returnTime,
                           @Param("fineAmount") BigDecimal fineAmount);

    int updateRenewStatus(@Param("id") Integer id,
                          @Param("dueDate") LocalDateTime dueDate,
                          @Param("renewCount") Integer renewCount,
                          @Param("expectedDueDate") LocalDateTime expectedDueDate,
                          @Param("expectedRenewCount") Integer expectedRenewCount);

    List<BorrowRecordView> query(BorrowRecordPageQuery dto);

    Integer queryCount(BorrowRecordPageQuery dto);

    int getActiveCountByUserId(@Param("userId") Integer userId);

    int countActiveByUserIdAndBookId(@Param("userId") Integer userId,
                                     @Param("bookId") Integer bookId);

    int countActiveByBookId(@Param("bookId") Integer bookId);

    int countActiveByBookIds(@Param("bookIds") List<Integer> bookIds);

    int countByUserIds(@Param("userIds") List<Integer> userIds);

    BigDecimal sumFineAmountByUserId(@Param("userId") Integer userId);

    List<DailyCount> dailyBorrowStats(@Param("startTime") LocalDateTime startTime,
                                      @Param("endTime") LocalDateTime endTime);

    List<Map<String, Object>> overdueUserStats();

    List<Map<String, Object>> hotBookStats(@Param("limit") Integer limit);

    List<Map<String, Object>> findDueReminders(@Param("startTime") LocalDateTime startTime,
                                               @Param("endTime") LocalDateTime endTime,
                                               @Param("afterId") Integer afterId,
                                               @Param("limit") Integer limit);

    int markDueReminderSent(@Param("id") Integer id,
                            @Param("reminderTime") LocalDateTime reminderTime);
}
