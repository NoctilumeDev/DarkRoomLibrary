package org.darkroomlibrary.mapper;

import org.darkroomlibrary.web.dto.query.UserPageQuery;
import org.darkroomlibrary.domain.model.User;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface UserMapper extends BaseMapper<User> {

    default User getById(Integer id) { return selectById(id); }
    default int update(User user) { return updateById(user); }

    User getByActive(User user);

    User findByIdForUpdate(@Param("id") Integer id);

    void batchDelete(@Param("ids") List<Integer> ids);

    List<User> query(UserPageQuery dto);

    Integer queryCount(UserPageQuery dto);
}
