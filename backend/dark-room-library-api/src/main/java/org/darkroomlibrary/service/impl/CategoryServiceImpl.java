package org.darkroomlibrary.service.impl;

import org.darkroomlibrary.mapper.BookMapper;
import org.darkroomlibrary.mapper.CategoryMapper;
import org.darkroomlibrary.web.response.ApiResponse;
import org.darkroomlibrary.web.response.PageResponse;
import org.darkroomlibrary.web.dto.query.CategoryPageQuery;
import org.darkroomlibrary.domain.model.Category;
import org.darkroomlibrary.service.CategoryService;
import org.darkroomlibrary.utils.IdListUtils;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.interceptor.TransactionAspectSupport;

import jakarta.annotation.Resource;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 分类服务实现
 */
@Service
public class CategoryServiceImpl implements CategoryService {

    @Resource
    private CategoryMapper categoryMapper;

    @Resource
    private BookMapper bookMapper;

    @Override
    @Transactional
    public ApiResponse<Void> save(Category category) {
        String validationError = validate(category, false);
        if (validationError != null) {
            return ApiResponse.error(validationError);
        }
        category.setName(category.getName().trim());
        category.setCreateTime(LocalDateTime.now());
        try {
            if (categoryMapper.insert(category) != 1) {
                return ApiResponse.error("分类新增失败，请重试");
            }
        } catch (DuplicateKeyException e) {
            return ApiResponse.error("分类名称已存在");
        }
        return ApiResponse.success("新增分类成功");
    }

    @Override
    @Transactional
    public ApiResponse<Void> update(Category category) {
        String validationError = validate(category, true);
        if (validationError != null) {
            return ApiResponse.error(validationError);
        }
        Category existing = categoryMapper.findByIdForUpdate(category.getId());
        if (existing == null) {
            return ApiResponse.error("分类不存在");
        }
        String newName = category.getName().trim();
        category.setName(newName);
        try {
            if (categoryMapper.update(category) == 0) {
                return ApiResponse.error("分类状态已变化，请刷新后重试");
            }
        } catch (DuplicateKeyException e) {
            return ApiResponse.error("分类名称已存在");
        }
        if (!existing.getName().equals(newName)) {
            bookMapper.updateCategoryName(existing.getName(), newName);
        }
        return ApiResponse.success("修改分类成功");
    }

    @Override
    @Transactional
    public ApiResponse<Void> batchDelete(List<Integer> ids) {
        List<Integer> normalizedIds = IdListUtils.normalize(ids);
        if (normalizedIds.isEmpty()) {
            return ApiResponse.error("请选择要删除的分类");
        }
        if (IdListUtils.exceedsBatchLimit(normalizedIds)) {
            return ApiResponse.error("单次最多删除" + IdListUtils.MAX_BATCH_SIZE + "个分类");
        }
        List<Category> categories = categoryMapper.findByIdsForUpdate(normalizedIds);
        if (categories.size() != normalizedIds.size()) {
            return ApiResponse.error("部分分类不存在");
        }
        List<String> names = categories.stream()
                .map(Category::getName)
                .collect(Collectors.toList());
        if (bookMapper.countByCategories(names) > 0) {
            return ApiResponse.error("分类仍被图书使用，请先调整相关图书");
        }
        if (categoryMapper.batchDelete(normalizedIds) != normalizedIds.size()) {
            TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
            return ApiResponse.error("分类状态已变化，请刷新后重试");
        }
        return ApiResponse.success("删除分类成功");
    }

    @Override
    public ApiResponse<List<Category>> query(CategoryPageQuery dto) {
        List<Category> list = categoryMapper.query(dto);
        Integer total = categoryMapper.queryCount(dto);
        return PageResponse.success(list, total);
    }

    @Override
    public ApiResponse<List<Category>> queryAll() {
        return ApiResponse.success(categoryMapper.queryAll());
    }

    private String validate(Category category, boolean requireId) {
        if (category == null) {
            return "分类参数不能为空";
        }
        if (requireId && category.getId() == null) {
            return "分类编号不能为空";
        }
        if (category.getName() == null || category.getName().trim().isEmpty()) {
            return "分类名称不能为空";
        }
        if (category.getName().trim().length() > 50) {
            return "分类名称不能超过50个字符";
        }
        return null;
    }
}
