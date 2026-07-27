package org.darkroomlibrary.service.impl;

import org.darkroomlibrary.mapper.BookReviewMapper;
import org.darkroomlibrary.mapper.BookReviewReportMapper;
import org.darkroomlibrary.web.response.ApiResponse;
import org.darkroomlibrary.web.response.PageResponse;
import org.darkroomlibrary.web.dto.query.BookReviewReportPageQuery;
import org.darkroomlibrary.domain.model.BookReview;
import org.darkroomlibrary.domain.model.BookReviewReport;
import org.darkroomlibrary.web.view.BookReviewReportView;
import org.darkroomlibrary.service.OperationAuditService;
import org.darkroomlibrary.service.BookReviewReportService;
import org.darkroomlibrary.service.support.RecommendationSourceVersionService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.interceptor.TransactionAspectSupport;

import jakarta.annotation.Resource;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class BookReviewReportServiceImpl implements BookReviewReportService {

    @Resource
    private BookReviewReportMapper bookReviewReportMapper;
    @Resource
    private BookReviewMapper bookReviewMapper;
    @Resource
    private OperationAuditService operationAuditService;
    @Resource
    private RecommendationSourceVersionService recommendationSourceVersionService;

    @Override
    public ApiResponse<List<BookReviewReportView>> query(BookReviewReportPageQuery dto) {
        List<BookReviewReportView> list = bookReviewReportMapper.query(dto);
        Integer total = bookReviewReportMapper.queryCount(dto);
        return PageResponse.success(list, total);
    }

    @Override
    @Transactional
    public ApiResponse<Void> ignore(Integer reportId) {
        BookReviewReport report = bookReviewReportMapper.findByIdForUpdate(reportId);
        if (report == null) {
            return ApiResponse.error("举报记录不存在");
        }
        if (report.getStatus() != null && report.getStatus() != 0) {
            return ApiResponse.error("该举报已处理");
        }
        if (bookReviewReportMapper.updateById(BookReviewReport.builder()
                .id(reportId)
                .status(2)
                .handleTime(LocalDateTime.now())
                .build()) == 0) {
            return ApiResponse.error("举报记录状态已变化，请刷新后重试");
        }
        operationAuditService.record("审核", "书评举报",
                reportDetail(report) + "，处理结果=忽略举报");
        return ApiResponse.success("已忽略举报");
    }

    @Override
    @Transactional
    public ApiResponse<Void> hideReview(Integer reportId) {
        BookReviewReport snapshot = reportId == null ? null : bookReviewReportMapper.selectById(reportId);
        if (snapshot == null) {
            return ApiResponse.error("举报记录不存在");
        }
        BookReview review = bookReviewMapper.findByIdForUpdate(snapshot.getReviewId());
        if (review == null) {
            return ApiResponse.error("被举报书评不存在");
        }
        BookReviewReport report = bookReviewReportMapper.findByIdForUpdate(reportId);
        if (report == null || !snapshot.getReviewId().equals(report.getReviewId())) {
            return ApiResponse.error("举报记录状态已变化，请刷新后重试");
        }
        if (report.getStatus() != null && report.getStatus() != 0) {
            return ApiResponse.error("该举报已处理");
        }
        if (bookReviewMapper.updateById(BookReview.builder()
                .id(review.getId())
                .status(1)
                .build()) == 0) {
            return ApiResponse.error("书评状态已变化，请刷新后重试");
        }
        if (bookReviewReportMapper.updateById(BookReviewReport.builder()
                .id(reportId)
                .status(1)
                .handleTime(LocalDateTime.now())
                .build()) == 0) {
            TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
            return ApiResponse.error("举报记录状态已变化，请刷新后重试");
        }
        operationAuditService.record("审核", "书评举报",
                reportDetail(report) + "，处理结果=隐藏书评，书评作者ID=" + review.getUserId());
        recommendationSourceVersionService.invalidateUserAndGlobalAfterCommit(review.getUserId());
        return ApiResponse.success("已隐藏书评");
    }

    private String reportDetail(BookReviewReport report) {
        return "举报ID=" + report.getId()
                + "，书评ID=" + report.getReviewId()
                + "，举报人ID=" + report.getUserId()
                + "，举报原因=" + report.getReason();
    }
}
