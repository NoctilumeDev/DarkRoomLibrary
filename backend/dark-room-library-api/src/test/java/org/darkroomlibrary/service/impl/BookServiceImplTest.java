package org.darkroomlibrary.service.impl;

import org.darkroomlibrary.BaseTest;
import org.darkroomlibrary.mapper.BookReservationMapper;
import org.darkroomlibrary.mapper.BookshelfMapper;
import org.darkroomlibrary.mapper.CategoryMapper;
import org.darkroomlibrary.mapper.ProcurementOrderMapper;
import org.darkroomlibrary.web.response.ApiResponse;
import org.darkroomlibrary.web.dto.query.BookPageQuery;
import org.darkroomlibrary.domain.model.Book;
import org.darkroomlibrary.domain.model.BookReservation;
import org.darkroomlibrary.domain.model.Bookshelf;
import org.darkroomlibrary.domain.model.Category;
import org.darkroomlibrary.domain.model.ProcurementOrder;
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

    @Resource
    private ProcurementOrderMapper procurementOrderMapper;

    @Resource
    private BookReservationMapper bookReservationMapper;

    @Resource
    private BookshelfMapper bookshelfMapper;

    @Resource
    private CategoryMapper categoryMapper;

    @BeforeEach
    void setUp() {
        clearContext();
    }

    @Test
    @Order(1)
    @DisplayName("新增图书成功")
    void testSaveBookSuccess() {
        categoryMapper.insert(Category.builder()
                .name("测试分类")
                .createTime(LocalDateTime.now())
                .build());
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
    @Order(11)
    @DisplayName("新增图书时拒绝不存在的分类")
    void testSaveBookRejectsMissingCategory() {
        Book book = Book.builder()
                .name("无效分类图书")
                .author("测试作者")
                .category("不存在分类")
                .totalCount(1)
                .availableCount(1)
                .build();

        ApiResponse<Void> result = bookService.save(book);

        assertEquals(400, result.getCode());
    }

    @Test
    @Order(2)
    @DisplayName("更新图书成功")
    void testUpdateBookSuccess() {
        Book book = createTestBook("更新前图书", "更新前作者", 5);
        book.setOriginalTotalCount(book.getTotalCount());
        book.setOriginalAvailableCount(book.getAvailableCount());
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
        var user = createTestUser("bookborrow01", "库存测试读者", "bookborrow01@example.test");
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
        var user = createTestUser("bookstock01", "库存边界读者", "bookstock01@example.test");
        Book book = createTestBook("库存边界图书", "库存边界作者", 2);
        createTestBorrowRecord(user.getId(), book.getId(), LocalDateTime.now().plusDays(7));
        Book update = Book.builder()
                .id(book.getId())
                .totalCount(0)
                .availableCount(0)
                .originalTotalCount(book.getTotalCount())
                .originalAvailableCount(book.getAvailableCount())
                .version(book.getVersion())
                .build();

        ApiResponse<Void> result = bookService.update(update);

        assertEquals(400, result.getCode());
        assertEquals(2, bookMapper.getById(book.getId()).getTotalCount());
    }

    @Test
    @Order(8)
    @DisplayName("进行中的采购单阻止图书下架，终态采购单不阻止")
    void testActiveProcurementPreventsBookDeletion() {
        Book activeBook = createTestBook("采购中不可删除", "采购作者", 1);
        Book completedBook = createTestBook("采购完成可删除", "采购作者", 1);
        procurementOrderMapper.insert(procurementOrder(activeBook, 2));
        procurementOrderMapper.insert(procurementOrder(completedBook, 6));

        ApiResponse<Void> activeResult = bookService.batchDelete(List.of(activeBook.getId()));
        ApiResponse<Void> completedResult = bookService.batchDelete(List.of(completedBook.getId()));

        assertEquals(400, activeResult.getCode());
        assertFalse(Boolean.TRUE.equals(bookMapper.getById(activeBook.getId()).getIsDeleted()));
        assertEquals(200, completedResult.getCode());
        assertTrue(Boolean.TRUE.equals(bookMapper.getById(completedBook.getId()).getIsDeleted()));
    }

    private ProcurementOrder procurementOrder(Book book, int status) {
        LocalDateTime now = LocalDateTime.now();
        return ProcurementOrder.builder()
                .bookId(book.getId())
                .bookName(book.getName())
                .requestCount(2)
                .status(status)
                .stockApplied(status >= 5)
                .createTime(now)
                .updateTime(now)
                .build();
    }

    @Test
    @Order(9)
    @DisplayName("库存调整不能低于已通知预约数量")
    void testUpdateBookStockBelowNotifiedReservationsRejected() {
        var first = createTestUser("notified_stock_1", "预约库存读者一", "notified-stock-1@example.test");
        var second = createTestUser("notified_stock_2", "预约库存读者二", "notified-stock-2@example.test");
        Book book = createTestBook("预约库存边界图书", "预约作者", 2);
        bookReservationMapper.insert(notifiedReservation(first.getId(), book.getId()));
        bookReservationMapper.insert(notifiedReservation(second.getId(), book.getId()));

        ApiResponse<Void> result = bookService.update(Book.builder()
                .id(book.getId())
                .totalCount(2)
                .availableCount(1)
                .originalTotalCount(book.getTotalCount())
                .originalAvailableCount(book.getAvailableCount())
                .version(book.getVersion())
                .build());

        assertEquals(400, result.getCode());
        assertEquals(2, bookMapper.getById(book.getId()).getAvailableCount());
    }

    private BookReservation notifiedReservation(Integer userId, Integer bookId) {
        return BookReservation.builder()
                .userId(userId)
                .bookId(bookId)
                .reserveTime(LocalDateTime.now().minusHours(1))
                .status(3)
                .notifyTime(LocalDateTime.now())
                .build();
    }

    @Test
    @Order(10)
    @DisplayName("完整编辑可以清空图书书架")
    void testFullUpdateCanClearBookshelf() {
        Bookshelf shelf = Bookshelf.builder()
                .name("清空书架-" + System.nanoTime())
                .capacity(100)
                .createTime(LocalDateTime.now())
                .build();
        bookshelfMapper.insert(shelf);
        Book book = createTestBook("清空书架图书", "清空作者", 2);
        bookMapper.update(Book.builder().id(book.getId()).bookshelfId(shelf.getId()).build());
        Book stored = bookMapper.getById(book.getId());

        ApiResponse<Void> result = bookService.update(Book.builder()
                .id(stored.getId())
                .name(stored.getName())
                .author(stored.getAuthor())
                .isbn(stored.getIsbn())
                .publisher(stored.getPublisher())
                .category(stored.getCategory())
                .totalCount(stored.getTotalCount())
                .availableCount(stored.getAvailableCount())
                .originalTotalCount(stored.getTotalCount())
                .originalAvailableCount(stored.getAvailableCount())
                .cover(stored.getCover())
                .description(stored.getDescription())
                .bookshelfId(null)
                .version(stored.getVersion())
                .build());

        assertEquals(200, result.getCode());
        assertNull(bookMapper.getById(book.getId()).getBookshelfId());
    }

    @Test
    @Order(12)
    @DisplayName("旧编辑表单不能覆盖并发入库后的库存")
    void testStaleBookEditCannotOverwriteConcurrentStockIncrease() {
        Book book = createTestBook("并发库存编辑图书", "并发作者", 2);
        Book editSnapshot = bookMapper.getById(book.getId());
        assertEquals(1, bookMapper.increaseStock(book.getId(), 3));

        ApiResponse<Void> result = bookService.update(Book.builder()
                .id(editSnapshot.getId())
                .name("旧表单修改后的书名")
                .author(editSnapshot.getAuthor())
                .isbn(editSnapshot.getIsbn())
                .publisher(editSnapshot.getPublisher())
                .category(editSnapshot.getCategory())
                .totalCount(editSnapshot.getTotalCount())
                .availableCount(editSnapshot.getAvailableCount())
                .originalTotalCount(editSnapshot.getTotalCount())
                .originalAvailableCount(editSnapshot.getAvailableCount())
                .cover(editSnapshot.getCover())
                .description(editSnapshot.getDescription())
                .bookshelfId(editSnapshot.getBookshelfId())
                .version(editSnapshot.getVersion())
                .build());

        Book stored = bookMapper.getById(book.getId());
        assertEquals(400, result.getCode());
        assertEquals(5, stored.getTotalCount());
        assertEquals(5, stored.getAvailableCount());
        assertEquals(book.getName(), stored.getName());
    }

    @Test
    @Order(13)
    @DisplayName("旧编辑表单不能覆盖其他管理员已保存的图书元数据")
    void testStaleBookEditCannotOverwriteConcurrentMetadataUpdate() {
        Book book = createTestBook("元数据并发编辑图书", "并发作者", 2);
        Book firstSnapshot = bookMapper.getById(book.getId());
        Book staleSnapshot = bookMapper.getById(book.getId());

        ApiResponse<Void> firstResult = bookService.update(Book.builder()
                .id(firstSnapshot.getId())
                .version(firstSnapshot.getVersion())
                .name("管理员甲已更新书名")
                .author(firstSnapshot.getAuthor())
                .isbn(firstSnapshot.getIsbn())
                .publisher(firstSnapshot.getPublisher())
                .category(firstSnapshot.getCategory())
                .totalCount(firstSnapshot.getTotalCount())
                .availableCount(firstSnapshot.getAvailableCount())
                .originalTotalCount(firstSnapshot.getTotalCount())
                .originalAvailableCount(firstSnapshot.getAvailableCount())
                .cover(firstSnapshot.getCover())
                .description(firstSnapshot.getDescription())
                .bookshelfId(firstSnapshot.getBookshelfId())
                .build());

        ApiResponse<Void> staleResult = bookService.update(Book.builder()
                .id(staleSnapshot.getId())
                .version(staleSnapshot.getVersion())
                .name(staleSnapshot.getName())
                .author("管理员乙的旧表单作者")
                .isbn(staleSnapshot.getIsbn())
                .publisher(staleSnapshot.getPublisher())
                .category(staleSnapshot.getCategory())
                .totalCount(staleSnapshot.getTotalCount())
                .availableCount(staleSnapshot.getAvailableCount())
                .originalTotalCount(staleSnapshot.getTotalCount())
                .originalAvailableCount(staleSnapshot.getAvailableCount())
                .cover(staleSnapshot.getCover())
                .description(staleSnapshot.getDescription())
                .bookshelfId(staleSnapshot.getBookshelfId())
                .build());

        Book stored = bookMapper.getById(book.getId());
        assertEquals(200, firstResult.getCode());
        assertEquals(400, staleResult.getCode());
        assertEquals("管理员甲已更新书名", stored.getName());
        assertEquals(firstSnapshot.getAuthor(), stored.getAuthor());
        assertEquals(1, stored.getVersion());
    }
}
