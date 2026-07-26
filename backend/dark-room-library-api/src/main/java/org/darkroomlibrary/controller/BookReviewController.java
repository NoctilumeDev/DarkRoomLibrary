package org.darkroomlibrary.controller;

import org.darkroomlibrary.aop.NormalizePageQuery;
import org.darkroomlibrary.aop.RequireRole;
import org.darkroomlibrary.domain.type.UserRole;
import org.darkroomlibrary.aop.ManualAudit;
import org.darkroomlibrary.web.response.ApiResponse;
import org.darkroomlibrary.web.dto.query.BookReviewPageQuery;
import org.darkroomlibrary.web.dto.command.BookReviewCreateDto;
import org.darkroomlibrary.web.dto.command.BookReviewReplyDto;
import org.darkroomlibrary.web.dto.command.BookReviewReportDto;
import org.darkroomlibrary.web.dto.command.BookReviewUpdateDto;
import org.darkroomlibrary.web.view.BookReviewView;
import org.darkroomlibrary.service.BookReviewService;
import org.springframework.web.bind.annotation.*;

import jakarta.annotation.Resource;
import java.util.List;
import jakarta.validation.Valid;

/**
 * 图书评价控制器
 */
@RestController
@RequestMapping("/bookReview")
public class BookReviewController {

    @Resource
    private BookReviewService bookReviewService;

    /**
     * 新增评价
     */
    @RequireRole(UserRole.READER)
    @PostMapping("/save")
    public ApiResponse<Void> save(@Valid @RequestBody BookReviewCreateDto dto) {
        return bookReviewService.save(dto);
    }

    @RequireRole(UserRole.READER)
    @PutMapping("/update")
    public ApiResponse<Void> update(@Valid @RequestBody BookReviewUpdateDto dto) {
        return bookReviewService.update(dto);
    }

    @RequireRole({UserRole.READER, UserRole.ADMIN})
    @ManualAudit
    @PostMapping("/batchDelete")
    public ApiResponse<Void> batchDelete(@RequestBody List<Integer> ids) {
        return bookReviewService.batchDelete(ids);
    }

    @NormalizePageQuery
    @RequireRole({UserRole.READER, UserRole.ADMIN})
    @PostMapping("/query")
    public ApiResponse<List<BookReviewView>> query(@RequestBody BookReviewPageQuery dto) {
        return bookReviewService.query(dto);
    }

    @RequireRole(UserRole.READER)
    @PostMapping("/like/{reviewId}")
    public ApiResponse<Boolean> like(@PathVariable Integer reviewId) {
        return bookReviewService.toggleLike(reviewId);
    }

    @RequireRole(UserRole.READER)
    @PostMapping("/reply/{reviewId}")
    public ApiResponse<Void> reply(
            @PathVariable Integer reviewId,
            @Valid @RequestBody BookReviewReplyDto dto) {
        return bookReviewService.reply(reviewId, dto.getContent(), dto.getReplyToUserId());
    }

    @RequireRole(UserRole.READER)
    @PostMapping("/report/{reviewId}")
    public ApiResponse<Void> report(
            @PathVariable Integer reviewId,
            @Valid @RequestBody BookReviewReportDto dto) {
        return bookReviewService.report(reviewId, dto.getReason());
    }
}
