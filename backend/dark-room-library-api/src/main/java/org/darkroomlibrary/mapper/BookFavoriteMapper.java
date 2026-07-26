package org.darkroomlibrary.mapper;

import org.darkroomlibrary.web.dto.query.BookFavoritePageQuery;
import org.darkroomlibrary.domain.model.BookFavorite;
import org.darkroomlibrary.web.view.BookFavoriteView;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface BookFavoriteMapper extends BaseMapper<BookFavorite> {

    Integer isFavorited(@Param("userId") Integer userId, @Param("bookId") Integer bookId);

    void removeByUserAndBook(@Param("userId") Integer userId, @Param("bookId") Integer bookId);

    List<BookFavoriteView> query(BookFavoritePageQuery dto);

    Integer queryCount(BookFavoritePageQuery dto);
}
