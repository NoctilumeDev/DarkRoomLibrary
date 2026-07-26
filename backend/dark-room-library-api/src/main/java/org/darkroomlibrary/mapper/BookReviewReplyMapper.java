package org.darkroomlibrary.mapper;

import org.darkroomlibrary.domain.model.BookReviewReply;
import org.darkroomlibrary.web.view.BookReviewReplyView;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface BookReviewReplyMapper extends BaseMapper<BookReviewReply> {

    List<BookReviewReplyView> queryByReviewIds(@Param("reviewIds") List<Integer> reviewIds);

    int deleteByReviewIds(@Param("reviewIds") List<Integer> reviewIds);
}
