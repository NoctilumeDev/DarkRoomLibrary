package org.darkroomlibrary.mapper;

import org.darkroomlibrary.web.dto.query.MessageBoardPageQuery;
import org.darkroomlibrary.domain.model.MessageBoard;
import org.darkroomlibrary.web.view.MessageBoardView;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface MessageBoardMapper extends BaseMapper<MessageBoard> {

    default MessageBoard getById(Integer id) { return selectById(id); }
    default int update(MessageBoard entity) { return updateById(entity); }
    MessageBoard findByIdForUpdate(@Param("id") Integer id);

    List<MessageBoard> findByIdsForUpdate(@Param("ids") List<Integer> ids);

    List<MessageBoardView> query(MessageBoardPageQuery dto);

    Integer queryCount(MessageBoardPageQuery dto);

    int moveToRecycleBin(@Param("ids") List<Integer> ids,
                         @Param("userId") Integer userId,
                         @Param("deletedAt") java.time.LocalDateTime deletedAt,
                         @Param("restoreDeadline") java.time.LocalDateTime restoreDeadline);

    int hideByAdministrator(@Param("ids") List<Integer> ids);

    int restoreFromRecycleBin(@Param("ids") List<Integer> ids,
                              @Param("userId") Integer userId,
                              @Param("now") java.time.LocalDateTime now);

    List<MessageBoardView> queryRecycleBin(@Param("userId") Integer userId,
                                           @Param("current") Integer current,
                                           @Param("size") Integer size,
                                           @Param("now") java.time.LocalDateTime now);

    Integer queryRecycleBinCount(@Param("userId") Integer userId,
                                 @Param("now") java.time.LocalDateTime now);

    List<Integer> findExpirationCandidates(@Param("now") java.time.LocalDateTime now,
                                           @Param("limit") Integer limit);

    int markExpired(@Param("id") Integer id,
                    @Param("restoreDeadline") java.time.LocalDateTime restoreDeadline,
                    @Param("now") java.time.LocalDateTime now);
}
