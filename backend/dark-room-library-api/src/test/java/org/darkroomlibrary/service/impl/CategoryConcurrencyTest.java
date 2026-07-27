package org.darkroomlibrary.service.impl;

import org.darkroomlibrary.domain.model.Category;
import org.darkroomlibrary.mapper.BookMapper;
import org.darkroomlibrary.mapper.CategoryMapper;
import org.darkroomlibrary.service.support.RecommendationSourceVersionService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CategoryConcurrencyTest {

    @Mock
    private CategoryMapper categoryMapper;

    @Mock
    private BookMapper bookMapper;

    @Mock
    private RecommendationSourceVersionService recommendationSourceVersionService;

    @InjectMocks
    private CategoryServiceImpl categoryService;

    @Test
    void categoryRenameLocksTheRowBeforeUpdatingDenormalizedBookNames() {
        Category existing = Category.builder().id(7).name("旧分类").build();
        when(categoryMapper.findByIdForUpdate(7)).thenReturn(existing);
        when(categoryMapper.update(org.mockito.ArgumentMatchers.any(Category.class))).thenReturn(1);

        categoryService.update(Category.builder().id(7).name("新分类").build());

        verify(categoryMapper).findByIdForUpdate(7);
        verify(bookMapper).updateCategoryName("旧分类", "新分类");
        verify(recommendationSourceVersionService).invalidateGlobalAfterCommit();
    }
}
