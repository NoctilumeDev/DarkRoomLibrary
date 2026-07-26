package org.darkroomlibrary.mapper;

import org.darkroomlibrary.web.dto.query.CategoryPageQuery;
import org.darkroomlibrary.domain.model.Category;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface CategoryMapper extends BaseMapper<Category> {

    default Category getById(Integer id) { return selectById(id); }
    default int update(Category entity) { return updateById(entity); }
    default int batchDelete(List<Integer> ids) { return deleteByIds(ids); }

    List<Category> query(CategoryPageQuery dto);

    Integer queryCount(CategoryPageQuery dto);

    List<Category> queryAll();
}
