package org.darkroomlibrary.service.impl;

import org.darkroomlibrary.BaseTest;
import org.darkroomlibrary.web.response.ApiResponse;
import org.darkroomlibrary.web.dto.query.BookshelfPageQuery;
import org.darkroomlibrary.domain.model.Bookshelf;
import org.darkroomlibrary.service.BookshelfService;
import org.junit.jupiter.api.*;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;

import jakarta.annotation.Resource;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 书架服务测试
 */
@SpringBootTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class BookshelfServiceImplTest extends BaseTest {

    @Resource
    private BookshelfService bookshelfService;

    private static Integer savedId;

    @BeforeEach
    void setUp() {
        clearContext();
    }

    @Test
    @Order(1)
    @DisplayName("新增书架成功")
    void testSaveSuccess() {
        Bookshelf bs = Bookshelf.builder()
                .name("A区-01架")
                .location("三楼东区")
                .capacity(200)
                .description("文学类图书")
                .build();
        ApiResponse<Void> result = bookshelfService.save(bs);
        assertEquals(200, result.getCode());
        assertNotNull(bs.getId());
        savedId = bs.getId();
    }

    @Test
    @Order(2)
    @DisplayName("查询全部书架")
    void testQueryAll() {
        ApiResponse<List<Bookshelf>> result = bookshelfService.queryAll();
        assertEquals(200, result.getCode());
        assertNotNull(result.getData());
        assertFalse(result.getData().isEmpty());
    }

    @Test
    @Order(3)
    @DisplayName("分页查询书架")
    void testQuery() {
        BookshelfPageQuery dto = new BookshelfPageQuery();
        dto.setName("A区");
        ApiResponse<List<Bookshelf>> result = bookshelfService.query(dto);
        assertEquals(200, result.getCode());
        assertNotNull(result.getData());
    }

    @Test
    @Order(4)
    @DisplayName("修改书架成功")
    void testUpdateSuccess() {
        Bookshelf bs = Bookshelf.builder()
                .id(savedId)
                .name("A区-01架-更新")
                .location("四楼西区")
                .capacity(300)
                .build();
        ApiResponse<Void> result = bookshelfService.update(bs);
        assertEquals(200, result.getCode());
    }

    @Test
    @Order(5)
    @DisplayName("修改不存在的书架返回错误")
    void testUpdateNotFound() {
        Bookshelf bs = Bookshelf.builder().id(99999).name("不存在").build();
        ApiResponse<Void> result = bookshelfService.update(bs);
        assertEquals(400, result.getCode());
    }

    @Test
    @Order(6)
    @DisplayName("删除书架成功")
    void testBatchDelete() {
        ApiResponse<Void> result = bookshelfService.batchDelete(Arrays.asList(savedId));
        assertEquals(200, result.getCode());
    }

    @Test
    @Order(7)
    @DisplayName("新增书架-默认容量")
    void testSaveDefaultCapacity() {
        Bookshelf bs = Bookshelf.builder()
                .name("B区-02架")
                .location("二楼中区")
                .build();
        ApiResponse<Void> result = bookshelfService.save(bs);
        assertEquals(200, result.getCode());
        assertEquals(100, bs.getCapacity());
    }

    @Test
    @Order(8)
    @DisplayName("空书架批量删除返回业务错误")
    void testEmptyBatchDeleteRejected() {
        assertEquals(400, bookshelfService.batchDelete(List.of()).getCode());
    }

    @Test
    @Order(9)
    @DisplayName("书架容量必须大于0")
    void testInvalidCapacityRejected() {
        Bookshelf bs = Bookshelf.builder()
                .name("无效容量书架")
                .capacity(0)
                .build();

        assertEquals(400, bookshelfService.save(bs).getCode());
    }
}
