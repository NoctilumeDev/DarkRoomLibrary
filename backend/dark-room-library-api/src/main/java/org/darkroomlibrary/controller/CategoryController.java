package org.darkroomlibrary.controller;

import org.darkroomlibrary.aop.NormalizePageQuery;
import org.darkroomlibrary.aop.RequireRole;
import org.darkroomlibrary.domain.type.UserRole;
import org.darkroomlibrary.web.response.ApiResponse;
import org.darkroomlibrary.web.dto.query.CategoryPageQuery;
import org.darkroomlibrary.domain.model.Category;
import org.darkroomlibrary.service.CategoryService;
import org.springframework.web.bind.annotation.*;

import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import java.util.List;

/**
 * 分类控制器
 */
@RestController
@RequestMapping("/category")
public class CategoryController {

    @Resource
    private CategoryService categoryService;

    @RequireRole({UserRole.ADMIN, UserRole.SUPER_ADMIN})
    @PostMapping("/save")
    public ApiResponse<Void> save(@Valid @RequestBody Category category) {
        return categoryService.save(category);
    }

    @RequireRole({UserRole.ADMIN, UserRole.SUPER_ADMIN})
    @PutMapping("/update")
    public ApiResponse<Void> update(@Valid @RequestBody Category category) {
        return categoryService.update(category);
    }

    @RequireRole({UserRole.ADMIN, UserRole.SUPER_ADMIN})
    @PostMapping("/batchDelete")
    public ApiResponse<Void> batchDelete(@RequestBody List<Integer> ids) {
        return categoryService.batchDelete(ids);
    }

    @NormalizePageQuery
    @PostMapping("/query")
    public ApiResponse<List<Category>> query(@RequestBody CategoryPageQuery dto) {
        return categoryService.query(dto);
    }

    @GetMapping("/queryAll")
    public ApiResponse<List<Category>> queryAll() {
        return categoryService.queryAll();
    }
}