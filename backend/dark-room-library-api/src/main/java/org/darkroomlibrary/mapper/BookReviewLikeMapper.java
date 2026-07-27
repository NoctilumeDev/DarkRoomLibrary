package org.darkroomlibrary.mapper;

import org.darkroomlibrary.domain.model.BookReviewLike;
import org.darkroomlibrary.web.view.InteractionSummary;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface BookReviewLikeMapper extends BaseMapper<BookReviewLike> {

    Integer countByReviewIdAndUserId(@Param("reviewId") Integer reviewId,
                                     @Param("userId") Integer userId);

    java.util.List<InteractionSummary> summarizeByReviewIds(
            @Param("reviewIds") java.util.List<Integer> reviewIds,
            @Param("userId") Integer userId);

    int deleteByReviewIdAndUserId(@Param("reviewId") Integer reviewId,
                                  @Param("userId") Integer userId);

    int deleteByReviewIds(@Param("reviewIds") java.util.List<Integer> reviewIds);
}
