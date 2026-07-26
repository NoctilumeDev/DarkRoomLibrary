package org.darkroomlibrary.mapper;

import org.darkroomlibrary.domain.model.BookReviewLike;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface BookReviewLikeMapper extends BaseMapper<BookReviewLike> {

    Integer countByReviewId(@Param("reviewId") Integer reviewId);

    Integer countByReviewIdAndUserId(@Param("reviewId") Integer reviewId,
                                     @Param("userId") Integer userId);

    int deleteByReviewIdAndUserId(@Param("reviewId") Integer reviewId,
                                  @Param("userId") Integer userId);

    int deleteByReviewIds(@Param("reviewIds") java.util.List<Integer> reviewIds);
}
