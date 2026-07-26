package org.darkroomlibrary.controller;

import org.darkroomlibrary.aop.NormalizePageQuery;
import org.darkroomlibrary.aop.RequireRole;
import org.darkroomlibrary.domain.type.UserRole;
import org.darkroomlibrary.web.response.ApiResponse;
import org.darkroomlibrary.web.dto.query.BookReviewReportPageQuery;
import org.darkroomlibrary.web.view.BookReviewReportView;
import org.darkroomlibrary.service.BookReviewReportService;
import org.springframework.web.bind.annotation.*;

import jakarta.annotation.Resource;
import java.util.List;

/**
 * 图书评价举报审核控制器
 */
@RestController
@RequestMapping("/bookReviewReport")
public class BookReviewReportController {

    @Resource
    private BookReviewReportService bookReviewReportService;

    @NormalizePageQuery
    @RequireRole({UserRole.ADMIN, UserRole.SUPER_ADMIN})
    @PostMapping("/query")
    public ApiResponse<List<BookReviewReportView>> query(@RequestBody BookReviewReportPageQuery dto) {
        return bookReviewReportService.query(dto);
    }

    @RequireRole({UserRole.ADMIN, UserRole.SUPER_ADMIN})
    @PostMapping("/ignore/{reportId}")
    public ApiResponse<Void> ignore(@PathVariable Integer reportId) {
        return bookReviewReportService.ignore(reportId);
    }

    @RequireRole({UserRole.ADMIN, UserRole.SUPER_ADMIN})
    @PostMapping("/hideReview/{reportId}")
    public ApiResponse<Void> hideReview(@PathVariable Integer reportId) {
        return bookReviewReportService.hideReview(reportId);
    }
}
