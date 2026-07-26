package org.darkroomlibrary.service;

import org.darkroomlibrary.web.response.ApiResponse;
import org.darkroomlibrary.web.dto.query.CategoryPageQuery;
import org.darkroomlibrary.domain.model.Category;

import java.util.List;

/**
 * 分类服务接口
 */
public interface CategoryService {

    ApiResponse<Void> save(Category category);

    ApiResponse<Void> update(Category category);

    ApiResponse<Void> batchDelete(List<Integer> ids);

    ApiResponse<List<Category>> query(CategoryPageQuery dto);

    ApiResponse<List<Category>> queryAll();
}