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
    default int batchDelete(List<Integer> ids) { return deleteByIds(ids); }

    List<BookReviewView> query(BookReviewPageQuery dto);

    Integer queryCount(BookReviewPageQuery dto);
}
