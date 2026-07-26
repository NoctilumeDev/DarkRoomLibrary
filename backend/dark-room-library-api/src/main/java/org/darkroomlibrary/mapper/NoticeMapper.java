package org.darkroomlibrary.mapper;

import org.darkroomlibrary.web.dto.query.NoticePageQuery;
import org.darkroomlibrary.domain.model.Notice;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface NoticeMapper extends BaseMapper<Notice> {

    default Notice getById(Integer id) { return selectById(id); }
    default int update(Notice entity) { return updateById(entity); }
    default int batchDelete(List<Integer> ids) { return deleteByIds(ids); }
    default int save(Notice notice) { return insert(notice); }

    List<Notice> query(NoticePageQuery noticePageQuery);

    Integer queryCount(NoticePageQuery noticePageQuery);
}
