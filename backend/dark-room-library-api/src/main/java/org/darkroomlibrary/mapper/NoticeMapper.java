package org.darkroomlibrary.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.darkroomlibrary.domain.model.Notice;
import org.darkroomlibrary.web.dto.query.NoticePageQuery;

import java.util.List;

@Mapper
public interface NoticeMapper extends BaseMapper<Notice> {

    Notice findByIdForUpdate(@Param("id") Integer id);

    List<Notice> findByIdsForUpdate(@Param("ids") List<Integer> ids);

    List<Notice> findPage(NoticePageQuery query);

    Integer countMatching(NoticePageQuery query);
}
