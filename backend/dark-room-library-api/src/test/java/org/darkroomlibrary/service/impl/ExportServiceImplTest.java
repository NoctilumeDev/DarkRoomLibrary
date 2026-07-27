package org.darkroomlibrary.service.impl;

import org.darkroomlibrary.BaseTest;
import org.darkroomlibrary.web.dto.query.BookPageQuery;
import org.darkroomlibrary.web.dto.query.BorrowRecordPageQuery;
import org.darkroomlibrary.web.dto.query.UserPageQuery;
import org.darkroomlibrary.domain.type.UserRole;
import org.darkroomlibrary.domain.model.User;
import org.darkroomlibrary.service.ExportService;
import com.alibaba.excel.EasyExcel;
import org.junit.jupiter.api.*;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.annotation.DirtiesContext;

import jakarta.annotation.Resource;
import java.io.ByteArrayInputStream;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 数据导出服务测试
 */
@SpringBootTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class ExportServiceImplTest extends BaseTest {

    @Resource
    private ExportService exportService;

    @BeforeEach
    void setUp() {
        clearContext();
        setCurrentUser(1, UserRole.ADMIN.code());
    }

    @Test
    @Order(1)
    @DisplayName("导出图书信息-空数据")
    void testExportBooks() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        BookPageQuery dto = new BookPageQuery();
        exportService.exportBooks(response, dto);

        assertEquals(200, response.getStatus());
        assertTrue(response.getContentType().contains("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));
        assertNotNull(response.getHeader("Content-Disposition"));
        assertTrue(response.getContentAsByteArray().length > 0);
    }

    @Test
    @Order(2)
    @DisplayName("导出借阅记录-空数据")
    void testExportBorrowRecords() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        BorrowRecordPageQuery dto = new BorrowRecordPageQuery();
        exportService.exportBorrowRecords(response, dto);

        assertEquals(200, response.getStatus());
        assertTrue(response.getContentAsByteArray().length > 0);
    }

    @Test
    @Order(3)
    @DisplayName("导出用户信息-空数据")
    void testExportUsers() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        exportService.exportUsers(response, null);

        assertEquals(200, response.getStatus());
        assertTrue(response.getContentAsByteArray().length > 0);
    }

    @Test
    @Order(4)
    @DisplayName("导出逾期记录-空数据")
    void testExportOverdueRecords() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        exportService.exportOverdueRecords(response);

        assertEquals(200, response.getStatus());
        assertTrue(response.getContentAsByteArray().length > 0);
    }

    @Test
    @Order(5)
    @DisplayName("导出图书-带筛选条件")
    void testExportBooksWithFilter() throws Exception {
        createTestBook("Java编程", "张三", 10);

        MockHttpServletResponse response = new MockHttpServletResponse();
        BookPageQuery dto = new BookPageQuery();
        dto.setName("Java");
        exportService.exportBooks(response, dto);

        assertEquals(200, response.getStatus());
        assertTrue(response.getContentAsByteArray().length > 0);
    }

    @Test
    @Order(6)
    @DisplayName("导出用户-带数据")
    void testExportUsersWithData() throws Exception {
        createTestUser("exportuser", "导出用户", "export@example.test");

        MockHttpServletResponse response = new MockHttpServletResponse();
        UserPageQuery dto = new UserPageQuery();
        exportService.exportUsers(response, dto);

        assertEquals(200, response.getStatus());
        assertTrue(response.getContentAsByteArray().length > 0);
    }

    @Test
    @Order(7)
    @DisplayName("导出失败-未登录无权限")
    void testExportRejectsAnonymousUser() throws Exception {
        clearContext();
        MockHttpServletResponse response = new MockHttpServletResponse();

        exportService.exportUsers(response, null);

        assertEquals(403, response.getStatus());
    }

    @Test
    @Order(8)
    @DisplayName("导出用户-普通管理员邮箱脱敏")
    void testExportUsersMasksEmailForAdmin() throws Exception {
        createTestUser("exportmask001", "导出脱敏用户", "exportmask001@example.test");

        MockHttpServletResponse response = new MockHttpServletResponse();
        exportService.exportUsers(response, new UserPageQuery());

        Map<Integer, String> row = findExportRow(response, "exportmask001");
        assertNotNull(row);
        assertEquals("exp***@example.test", row.get(2));
    }

    @Test
    @Order(9)
    @DisplayName("导出用户-超级管理员邮箱完整可见")
    void testExportUsersShowsFullEmailForSuperAdmin() throws Exception {
        setCurrentUser(1, UserRole.SUPER_ADMIN.code());
        createTestUser("exportfull001", "导出完整用户", "exportfull001@example.test");

        MockHttpServletResponse response = new MockHttpServletResponse();
        exportService.exportUsers(response, new UserPageQuery());

        Map<Integer, String> row = findExportRow(response, "exportfull001");
        assertNotNull(row);
        assertEquals("exportfull001@example.test", row.get(2));
    }

    @Test
    @Order(10)
    @DisplayName("导出用户-普通管理员筛选超级管理员仍不能绕过字段脱敏")
    void testAdminCannotBypassSuperAdminFieldMasking() {
        User superAdmin = createTestUser(
                "exportsuper001", "导出超级管理员", "exportsuper001@secret.test");
        userMapper.update(User.builder()
                .id(superAdmin.getId())
                .userRole(UserRole.SUPER_ADMIN.code())
                .isCoordinatorAdmin(false)
                .build());

        setCurrentUser(1, UserRole.ADMIN.code());
        UserPageQuery dto = new UserPageQuery();
        dto.setRole(UserRole.SUPER_ADMIN.code());
        MockHttpServletResponse response = new MockHttpServletResponse();
        exportService.exportUsers(response, dto);

        List<Map<Integer, String>> rows = readRows(response);
        Map<Integer, String> header = rows.get(0);
        Map<Integer, String> superAdminRow = rows.stream()
                .filter(row -> "exportsuper001".equals(row.get(0)))
                .findFirst()
                .orElseThrow();

        assertEquals("账号", header.get(0));
        assertEquals("昵称", header.get(1));
        assertEquals("邮箱", header.get(2));
        assertEquals("角色", header.get(3));
        assertEquals("注册时间", header.get(4));
        assertEquals("状态", header.get(5));
        assertEquals(6, header.size());
        assertEquals("exp***@secret.test", superAdminRow.get(2));
        assertFalse(superAdminRow.containsValue("exportsuper001@secret.test"));
        assertFalse(header.containsValue("密码"));
        assertFalse(header.containsValue("馆务协调员"));
    }

    private Map<Integer, String> findExportRow(MockHttpServletResponse response, String account) {
        return readRows(response).stream()
                .filter(row -> account.equals(row.get(0)))
                .findFirst()
                .orElse(null);
    }

    private List<Map<Integer, String>> readRows(MockHttpServletResponse response) {
        return EasyExcel
                .read(new ByteArrayInputStream(response.getContentAsByteArray()))
                .headRowNumber(0)
                .sheet()
                .doReadSync();
    }
}
