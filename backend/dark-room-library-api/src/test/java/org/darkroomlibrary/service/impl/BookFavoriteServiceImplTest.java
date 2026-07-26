package org.darkroomlibrary.service.impl;

import org.darkroomlibrary.BaseTest;
import org.darkroomlibrary.domain.model.Book;
import org.darkroomlibrary.domain.model.User;
import org.darkroomlibrary.service.BookFavoriteService;
import org.darkroomlibrary.web.response.ApiResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.annotation.DirtiesContext;

import jakarta.annotation.Resource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class BookFavoriteServiceImplTest extends BaseTest {

    @Resource
    private BookFavoriteService bookFavoriteService;

    private User reader;

    @BeforeEach
    void setUp() {
        clearContext();
        String suffix = String.valueOf(System.nanoTime());
        reader = createTestUser(
                "favorite_" + suffix,
                "收藏读者" + suffix,
                "favorite_" + suffix + "@test.com"
        );
        setCurrentUser(reader.getId(), reader.getUserRole());
    }

    @Test
    @DisplayName("收藏不存在的图书返回业务错误")
    void missingBookIsRejected() {
        ApiResponse<Void> result = bookFavoriteService.addFavorite(Integer.MAX_VALUE);

        assertEquals(400, result.getCode());
    }

    @Test
    @DisplayName("重复收藏保持单条记录并返回业务错误")
    void duplicateFavoriteIsRejected() {
        Book book = createTestBook("收藏测试图书-" + System.nanoTime(), "收藏作者", 1);

        ApiResponse<Void> first = bookFavoriteService.addFavorite(book.getId());
        ApiResponse<Void> duplicate = bookFavoriteService.addFavorite(book.getId());

        assertEquals(200, first.getCode());
        assertEquals(400, duplicate.getCode());
        assertTrue(bookFavoriteService.isFavorited(book.getId()).getData());
    }

    @Test
    @DisplayName("已下架图书不能收藏")
    void deletedBookIsRejected() {
        Book book = createTestBook("下架收藏图书-" + System.nanoTime(), "收藏作者", 1);
        bookMapper.softDelete(java.util.List.of(book.getId()));

        ApiResponse<Void> result = bookFavoriteService.addFavorite(book.getId());

        assertEquals(400, result.getCode());
        assertFalse(bookFavoriteService.isFavorited(book.getId()).getData());
    }
}
