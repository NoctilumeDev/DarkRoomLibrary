package org.darkroomlibrary.controller;

import org.darkroomlibrary.aop.RequireRole;
import org.darkroomlibrary.domain.type.UserRole;
import org.darkroomlibrary.web.dto.query.BookPageQuery;
import org.darkroomlibrary.web.dto.query.BorrowRecordPageQuery;
import org.darkroomlibrary.web.dto.query.UserPageQuery;
import org.darkroomlibrary.service.ExportService;
import org.springframework.web.bind.annotation.*;

import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletResponse;

/**
 * 数据导出控制器
 */
@RestController
@RequestMapping("/export")
public class ExportController {

    @Resource
    private ExportService exportService;

    /**
     * 导出借阅记录
     */
    @RequireRole({UserRole.ADMIN, UserRole.SUPER_ADMIN})
    @GetMapping("/borrowRecords")
    public void exportBorrowRecords(HttpServletResponse response,
                                    @RequestParam(required = false) Integer userId,
                                    @RequestParam(required = false) Integer bookId,
                                    @RequestParam(required = false) Boolean status) {
        BorrowRecordPageQuery dto = new BorrowRecordPageQuery();
        dto.setUserId(userId);
        dto.setBookId(bookId);
        dto.setStatus(status);
        exportService.exportBorrowRecords(response, dto);
    }

    /**
     * 导出图书信息
     */
    @RequireRole({UserRole.ADMIN, UserRole.SUPER_ADMIN})
    @GetMapping("/books")
    public void exportBooks(HttpServletResponse response,
                            @RequestParam(required = false) String name,
                            @RequestParam(required = false) String category) {
        BookPageQuery dto = new BookPageQuery();
        dto.setName(name);
        dto.setCategory(category);
        exportService.exportBooks(response, dto);
    }

    /**
     * 导出用户信息
     */
    @RequireRole({UserRole.ADMIN, UserRole.SUPER_ADMIN})
    @GetMapping("/users")
    public void exportUsers(HttpServletResponse response) {
        exportService.exportUsers(response, null);
    }

    /**
     * 导出逾期记录
     */
    @RequireRole({UserRole.ADMIN, UserRole.SUPER_ADMIN})
    @GetMapping("/overdueRecords")
    public void exportOverdueRecords(HttpServletResponse response) {
        exportService.exportOverdueRecords(response);
    }
}