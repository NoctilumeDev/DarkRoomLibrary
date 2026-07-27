package org.darkroomlibrary.mapper;

import org.darkroomlibrary.web.dto.query.UserPageQuery;
import org.darkroomlibrary.domain.model.User;
import org.darkroomlibrary.web.view.DailyCount;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface UserMapper extends BaseMapper<User> {

    default User getById(Integer id) { return selectById(id); }
    default int update(User user) { return updateById(user); }

    User getByActive(User user);

    User findByIdForUpdate(@Param("id") Integer id);

    List<User> findByIdsForUpdate(@Param("ids") List<Integer> ids);

    int batchDelete(@Param("ids") List<Integer> ids);

    List<User> query(UserPageQuery dto);

    Integer queryCount(UserPageQuery dto);

    List<DailyCount> dailyCreateStats(@Param("startTime") LocalDateTime startTime,
                                      @Param("endTime") LocalDateTime endTime);
}
