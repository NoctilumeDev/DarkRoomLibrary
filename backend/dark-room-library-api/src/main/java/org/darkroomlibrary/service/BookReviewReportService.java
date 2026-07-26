package org.darkroomlibrary.service;

import org.darkroomlibrary.web.response.ApiResponse;
import org.darkroomlibrary.web.dto.query.BookReviewReportPageQuery;
import org.darkroomlibrary.web.view.BookReviewReportView;

import java.util.List;

public interface BookReviewReportService {

    ApiResponse<List<BookReviewReportView>> query(BookReviewReportPageQuery dto);

    ApiResponse<Void> ignore(Integer reportId);

    ApiResponse<Void> hideReview(Integer reportId);
}
