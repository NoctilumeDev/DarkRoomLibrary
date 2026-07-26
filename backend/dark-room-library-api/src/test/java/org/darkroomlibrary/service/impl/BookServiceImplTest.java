package org.darkroomlibrary.service.impl;

import org.darkroomlibrary.BaseTest;
import org.darkroomlibrary.web.response.ApiResponse;
import org.darkroomlibrary.web.dto.query.BookPageQuery;
import org.darkroomlibrary.domain.model.Book;
import org.darkroomlibrary.service.BookService;
import org.junit.jupiter.api.*;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;

import jakarta.annotation.Resource;
import java.util.Arrays;
import java.util.List;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 图书服务测试
 */
@SpringBootTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class BookServiceImplTest extends BaseTest {

    @Resource
    private BookService bookService;

    @BeforeEach
    void setUp() {
        clearContext();
    }

    @Test
    @Order(1)
    @DisplayName("新增图书成功")
    void testSaveBookSuccess() {
        Book book = Book.builder()
                .name("单元测试图书")
                .author("测试作者")
                .isbn("9787123456780")
                .publisher("测试出版社")
                .category("测试分类")
                .totalCount(10)
                .cover("")
                .description("用于单元测试的图书")
                .build();
        ApiResponse<Void> result = bookService.save(book);
        assertNotNull(result);
        assertEquals(200, result.getCode());
    }

    @Test
    @Order(2)
    @DisplayName("更新图书成功")
    void testUpdateBookSuccess() {
        Book book = createTestBook("更新前图书", "更新前作者", 5);
        book.setName("更新后图书");
        book.setAuthor("更新后作者");
        ApiResponse<Void> result = bookService.update(book);
        assertNotNull(result);
        assertEquals(200, result.getCode());
    }

    @Test
    @Order(3)
    @DisplayName("软删除图书成功")
    void testSoftDeleteBookSuccess() {
        Book book = createTestBook("待删除图书", "待删除作者", 3);
        ApiResponse<Void> result = bookService.batchDelete(Arrays.asList(book.getId()));
        assertNotNull(result);
        assertEquals(200, result.getCode());

        // 查询应该过滤掉已删除的图书
        BookPageQuery dto = new BookPageQuery();
        dto.setName("待删除图书");
        dto.setCurrent(0);
        dto.setSize(10);
        List<Book> books = bookService.query(dto).getData();
        // 已删除的图书不应该出现在查询结果中
        boolean found = books.stream().anyMatch(b -> b.getId().equals(book.getId()));
        assertFalse(found, "已删除的图书不应该出现在查询结果中");
    }

    @Test
    @Order(4)
    @DisplayName("恢复图书成功")
    void testRestoreBookSuccess() {
        Book book = createTestBook("待恢复图书", "待恢复作者", 3);
        // 先删除
        bookService.batchDelete(Arrays.asList(book.getId()));
        // 再恢复
        ApiResponse<Void> result = bookService.restore(Arrays.asList(book.getId()));
        assertNotNull(result);
        assertEquals(200, result.getCode());

        // 查询应该能找到恢复的图书
        BookPageQuery dto = new BookPageQuery();
        dto.setName("待恢复图书");
        dto.setCurrent(0);
        dto.setSize(10);
        List<Book> books = bookService.query(dto).getData();
        boolean found = books.stream().anyMatch(b -> b.getId().equals(book.getId()));
        assertTrue(found, "恢复的图书应该出现在查询结果中");
    }

    @Test
    @Order(5)
    @DisplayName("查询过滤已删除图书")
    void testQueryExcludesDeleted() {
        Book activeBook = createTestBook("活动图书", "活动作者", 5);
        Book deletedBook = createTestBook("已删图书", "已删作者", 2);
        bookService.batchDelete(Arrays.asList(deletedBook.getId()));

        BookPageQuery dto = new BookPageQuery();
        dto.setCurrent(0);
        dto.setSize(Integer.MAX_VALUE);
        List<Book> books = bookService.query(dto).getData();

        // 活动图书应该在结果中
        boolean hasActive = books.stream().anyMatch(b -> b.getId().equals(activeBook.getId()));
        // 已删除图书不应该在结果中
        boolean hasDeleted = books.stream().anyMatch(b -> b.getId().equals(deletedBook.getId()));

        assertTrue(hasActive, "活动图书应该出现在查询结果中");
        assertFalse(hasDeleted, "已删除的图书不应该出现在查询结果中");
    }

    @Test
    @Order(6)
    @DisplayName("在借图书不能删除")
    void testDeleteBookWithActiveBorrowRejected() {
        var user = createTestUser("bookborrow01", "库存测试读者", "bookborrow01@test.com");
        Book book = createTestBook("在借不可删除", "库存作者", 2);
        createTestBorrowRecord(user.getId(), book.getId(), LocalDateTime.now().plusDays(7));

        ApiResponse<Void> result = bookService.batchDelete(List.of(book.getId()));

        assertEquals(400, result.getCode());
        assertFalse(Boolean.TRUE.equals(bookMapper.getById(book.getId()).getIsDeleted()));
    }

    @Test
    @Order(7)
    @DisplayName("库存调整不能小于当前在借数量")
    void testUpdateBookStockBelowActiveBorrowRejected() {
        var user = createTestUser("bookstock01", "库存边界读者", "bookstock01@test.com");
        Book book = createTestBook("库存边界图书", "库存边界作者", 2);
        createTestBorrowRecord(user.getId(), book.getId(), LocalDateTime.now().plusDays(7));
        Book update = Book.builder()
                .id(book.getId())
                .totalCount(0)
                .availableCount(0)
                .build();

        ApiResponse<Void> result = bookService.update(update);

        assertEquals(400, result.getCode());
        assertEquals(2, bookMapper.getById(book.getId()).getTotalCount());
    }
}
