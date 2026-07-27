package org.darkroomlibrary.mapper;

import org.darkroomlibrary.domain.model.NotificationTask;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface NotificationTaskMapper extends BaseMapper<NotificationTask> {

    default NotificationTask getById(Integer id) {
        return selectById(id);
    }

    List<NotificationTask> queryPending(@Param("now") LocalDateTime now, @Param("limit") int limit);

    int claimForProcessing(@Param("id") Integer id,
                           @Param("now") LocalDateTime now,
                           @Param("leaseUntil") LocalDateTime leaseUntil,
                           @Param("processingToken") String processingToken);

    int markSent(@Param("id") Integer id,
                 @Param("processingToken") String processingToken,
                 @Param("updateTime") LocalDateTime updateTime);

    int markFailed(@Param("id") Integer id,
                   @Param("processingToken") String processingToken,
                   @Param("status") Integer status,
                   @Param("retryCount") Integer retryCount,
                   @Param("lastError") String lastError,
                   @Param("nextRetryTime") LocalDateTime nextRetryTime,
                   @Param("updateTime") LocalDateTime updateTime);
}
