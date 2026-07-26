package org.darkroomlibrary.mapper;

import org.darkroomlibrary.web.dto.query.ProcurementMessagePageQuery;
import org.darkroomlibrary.domain.model.ProcurementMessage;
import org.darkroomlibrary.web.view.ProcurementMessageView;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface ProcurementMessageMapper extends BaseMapper<ProcurementMessage> {

    List<ProcurementMessageView> query(ProcurementMessagePageQuery dto);

    Integer queryCount(ProcurementMessagePageQuery dto);

    Integer countUnread(@Param("receiverId") Integer receiverId,
                        @Param("orderId") Integer orderId,
                        @Param("channelType") Integer channelType);

    int markRead(@Param("receiverId") Integer receiverId,
                 @Param("orderId") Integer orderId,
                 @Param("channelType") Integer channelType,
                 @Param("readTime") LocalDateTime readTime);
}
