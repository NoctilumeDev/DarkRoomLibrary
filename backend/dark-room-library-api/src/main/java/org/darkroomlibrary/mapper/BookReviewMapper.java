package org.darkroomlibrary.mapper;

import org.darkroomlibrary.web.dto.query.BookReviewPageQuery;
import org.darkroomlibrary.domain.model.BookReview;
import org.darkroomlibrary.web.view.BookReviewView;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface BookReviewMapper extends BaseMapper<BookReview> {

    default BookReview getById(Integer id) { return selectById(id); }
    default int update(BookReview entity) { return updateById(entity); }
    BookReview findByIdForUpdate(@Param("id") Integer id);

    List<BookReview> findByIdsForUpdate(@Param("ids") List<Integer> ids);

    List<BookReviewView> query(BookReviewPageQuery dto);

    Integer queryCount(BookReviewPageQuery dto);

    int moveToRecycleBin(@Param("ids") List<Integer> ids,
                         @Param("userId") Integer userId,
                         @Param("deletedAt") java.time.LocalDateTime deletedAt,
                         @Param("restoreDeadline") java.time.LocalDateTime restoreDeadline);

    int hideByAdministrator(@Param("ids") List<Integer> ids);

    int restoreFromRecycleBin(@Param("ids") List<Integer> ids,
                              @Param("userId") Integer userId,
                              @Param("now") java.time.LocalDateTime now);

    List<BookReviewView> queryRecycleBin(@Param("userId") Integer userId,
                                         @Param("current") Integer current,
                                         @Param("size") Integer size,
                                         @Param("now") java.time.LocalDateTime now);

    Integer queryRecycleBinCount(@Param("userId") Integer userId,
                                 @Param("now") java.time.LocalDateTime now);

    int markExpired(@Param("now") java.time.LocalDateTime now);
}
