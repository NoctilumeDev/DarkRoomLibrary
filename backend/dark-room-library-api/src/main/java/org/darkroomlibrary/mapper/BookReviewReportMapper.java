package org.darkroomlibrary.mapper;

import org.darkroomlibrary.domain.model.BookReviewReport;
import org.darkroomlibrary.web.dto.query.BookReviewReportPageQuery;
import org.darkroomlibrary.web.view.BookReviewReportView;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface BookReviewReportMapper extends BaseMapper<BookReviewReport> {

    BookReviewReport findByIdForUpdate(@Param("id") Integer id);

    Integer countByReviewId(@Param("reviewId") Integer reviewId);

    Integer countPendingByReviewId(@Param("reviewId") Integer reviewId);

    Integer countByReviewIdAndUserId(@Param("reviewId") Integer reviewId,
                                     @Param("userId") Integer userId);

    int deleteByReviewIds(@Param("reviewIds") java.util.List<Integer> reviewIds);

    List<BookReviewReportView> query(BookReviewReportPageQuery dto);

    Integer queryCount(BookReviewReportPageQuery dto);
}
