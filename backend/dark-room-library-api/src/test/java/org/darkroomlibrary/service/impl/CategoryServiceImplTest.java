package org.darkroomlibrary.service.impl;

import org.darkroomlibrary.BaseTest;
import org.darkroomlibrary.domain.model.Book;
import org.darkroomlibrary.domain.model.Category;
import org.darkroomlibrary.service.CategoryService;
import org.darkroomlibrary.web.response.ApiResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.annotation.DirtiesContext;

import jakarta.annotation.Resource;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class CategoryServiceImplTest extends BaseTest {

    @Resource
    private CategoryService categoryService;

    @Test
    @DisplayName("分类重命名同步更新图书分类")
    void categoryRenameUpdatesBooks() {
        String suffix = String.valueOf(System.nanoTime());
        Category category = Category.builder().name("旧分类-" + suffix).build();
        assertEquals(200, categoryService.save(category).getCode());
        Book book = createTestBook("分类同步图书-" + suffix, "分类作者", 1);
        bookMapper.update(Book.builder()
                .id(book.getId())
                .category(category.getName())
                .build());

        ApiResponse<Void> result = categoryService.update(Category.builder()
                .id(category.getId())
                .name("新分类-" + suffix)
                .build());

        assertEquals(200, result.getCode());
        assertEquals("新分类-" + suffix, bookMapper.getById(book.getId()).getCategory());
    }

    @Test
    @DisplayName("仍被图书引用的分类不能删除")
    void referencedCategoryCannotBeDeleted() {
        String suffix = String.valueOf(System.nanoTime());
        Category category = Category.builder().name("引用分类-" + suffix).build();
        assertEquals(200, categoryService.save(category).getCode());
        Book book = createTestBook("引用分类图书-" + suffix, "分类作者", 1);
        bookMapper.update(Book.builder()
                .id(book.getId())
                .category(category.getName())
                .build());

        ApiResponse<Void> result = categoryService.batchDelete(List.of(category.getId()));

        assertEquals(400, result.getCode());
    }

    @Test
    @DisplayName("重复分类名称返回业务错误")
    void duplicateCategoryNameIsRejected() {
        String name = "重复分类-" + System.nanoTime();
        assertEquals(200, categoryService.save(Category.builder().name(name).build()).getCode());

        ApiResponse<Void> duplicate = categoryService.save(Category.builder().name(name).build());

        assertEquals(400, duplicate.getCode());
    }

    @Test
    @DisplayName("空分类批量删除返回业务错误")
    void emptyBatchDeleteIsRejected() {
        assertEquals(400, categoryService.batchDelete(List.of()).getCode());
    }
}
