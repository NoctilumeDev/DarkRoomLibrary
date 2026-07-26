package org.darkroomlibrary.mapper;

import org.darkroomlibrary.web.dto.query.BookshelfPageQuery;
import org.darkroomlibrary.domain.model.Bookshelf;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface BookshelfMapper extends BaseMapper<Bookshelf> {

    default Bookshelf getById(Integer id) { return selectById(id); }
    default int update(Bookshelf entity) { return updateById(entity); }
    default int batchDelete(List<Integer> ids) { return deleteByIds(ids); }

    List<Bookshelf> query(BookshelfPageQuery dto);

    Integer queryCount(BookshelfPageQuery dto);

    List<Bookshelf> queryAll();
}
