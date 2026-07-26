package org.darkroomlibrary.service.impl;

import org.darkroomlibrary.infrastructure.cache.CacheService;
import org.darkroomlibrary.mapper.BookMapper;
import org.darkroomlibrary.mapper.CategoryMapper;
import org.darkroomlibrary.web.response.ApiResponse;
import org.darkroomlibrary.web.response.PageResponse;
import org.darkroomlibrary.web.dto.query.CategoryPageQuery;
import org.darkroomlibrary.domain.model.Category;
import org.darkroomlibrary.service.CategoryService;
import org.darkroomlibrary.utils.IdListUtils;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.annotation.Resource;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 分类服务实现
 */
@Slf4j
@Service
public class CategoryServiceImpl implements CategoryService {

    private static final String CATEGORY_ALL_CACHE_KEY = "cache:category:all";
    private static final Duration CATEGORY_CACHE_TTL = Duration.ofMinutes(10);

    @Resource
    private CategoryMapper categoryMapper;

    @Resource
    private BookMapper bookMapper;

    @Resource
    private CacheService cacheService;

    @Resource
    private ObjectMapper objectMapper;

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
            categoryMapper.insert(category);
        } catch (DuplicateKeyException e) {
            return ApiResponse.error("分类名称已存在");
        }
        cacheService.delete(CATEGORY_ALL_CACHE_KEY);
        return ApiResponse.success("新增分类成功");
    }

    @Override
    @Transactional
    public ApiResponse<Void> update(Category category) {
        String validationError = validate(category, true);
        if (validationError != null) {
            return ApiResponse.error(validationError);
        }
        Category existing = categoryMapper.getById(category.getId());
        if (existing == null) {
            return ApiResponse.error("分类不存在");
        }
        String newName = category.getName().trim();
        category.setName(newName);
        try {
            categoryMapper.update(category);
        } catch (DuplicateKeyException e) {
            return ApiResponse.error("分类名称已存在");
        }
        if (!existing.getName().equals(newName)) {
            bookMapper.updateCategoryName(existing.getName(), newName);
        }
        cacheService.delete(CATEGORY_ALL_CACHE_KEY);
        return ApiResponse.success("修改分类成功");
    }

    @Override
    @Transactional
    public ApiResponse<Void> batchDelete(List<Integer> ids) {
        List<Integer> normalizedIds = IdListUtils.normalize(ids);
        if (normalizedIds.isEmpty()) {
            return ApiResponse.error("请选择要删除的分类");
        }
        List<Category> categories = categoryMapper.selectByIds(normalizedIds);
        if (categories.size() != normalizedIds.size()) {
            return ApiResponse.error("部分分类不存在");
        }
        List<String> names = categories.stream()
                .map(Category::getName)
                .collect(Collectors.toList());
        if (bookMapper.countByCategories(names) > 0) {
            return ApiResponse.error("分类仍被图书使用，请先调整相关图书");
        }
        categoryMapper.batchDelete(normalizedIds);
        cacheService.delete(CATEGORY_ALL_CACHE_KEY);
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
        try {
            String cached = cacheService.getString(CATEGORY_ALL_CACHE_KEY).orElse(null);
            if (cached != null) {
                return ApiResponse.success(objectMapper.readValue(cached, new TypeReference<List<Category>>() {}));
            }
        } catch (Exception e) {
            log.warn("分类缓存读取失败，降级查询数据库: {}", e.getMessage());
        }
        List<Category> list = categoryMapper.queryAll();
        try {
            cacheService.setString(CATEGORY_ALL_CACHE_KEY, objectMapper.writeValueAsString(list), CATEGORY_CACHE_TTL);
        } catch (Exception e) {
            log.warn("分类缓存写入失败，忽略缓存: {}", e.getMessage());
        }
        return ApiResponse.success(list);
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
