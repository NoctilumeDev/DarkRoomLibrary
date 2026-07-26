package org.darkroomlibrary.controller;

import org.darkroomlibrary.aop.NormalizePageQuery;
import org.darkroomlibrary.aop.RequireRole;
import org.darkroomlibrary.domain.type.UserRole;
import org.darkroomlibrary.web.response.ApiResponse;
import org.darkroomlibrary.web.dto.query.BorrowRecordPageQuery;
import org.darkroomlibrary.web.view.BorrowRecordView;
import org.darkroomlibrary.service.BorrowRecordService;
import org.springframework.web.bind.annotation.*;

import jakarta.annotation.Resource;
import java.util.List;

/**
 * 借阅记录控制器
 */
@RestController
@RequestMapping("/borrowRecord")
public class BorrowRecordController {

    @Resource
    private BorrowRecordService borrowRecordService;

    /**
     * 读者借书
     *
     * @param bookId 图书ID
     * @return ApiResponse<Void>
     */
    @RequireRole(UserRole.READER)
    @PostMapping("/borrow/{bookId}")
    public ApiResponse<Void> borrow(@PathVariable Integer bookId) {
        return borrowRecordService.borrow(bookId);
    }

    /**
     * 读者归还本人借阅，管理员可代还
     *
     * @param recordId 记录ID
     * @return ApiResponse<Void>
     */
    @RequireRole({UserRole.READER, UserRole.ADMIN})
    @PostMapping("/return/{recordId}")
    public ApiResponse<Void> returnBook(@PathVariable Integer recordId) {
        return borrowRecordService.returnBook(recordId);
    }

    /**
     * 读者续借
     *
     * @param recordId 记录ID
     * @return ApiResponse<Void>
     */
    @RequireRole(UserRole.READER)
    @PostMapping("/renew/{recordId}")
    public ApiResponse<Void> renew(@PathVariable Integer recordId) {
        return borrowRecordService.renew(recordId);
    }

    /**
     * 分页查询借阅记录
     *
     * @param dto 查询参数
     * @return ApiResponse<List<BorrowRecordView>>
     */
    @NormalizePageQuery
    @RequireRole({UserRole.READER, UserRole.ADMIN})
    @PostMapping("/query")
    public ApiResponse<List<BorrowRecordView>> query(@RequestBody BorrowRecordPageQuery dto) {
        return borrowRecordService.query(dto);
    }
}
