package org.darkroomlibrary.mapper;

import org.darkroomlibrary.web.dto.query.BookPageQuery;
import org.darkroomlibrary.domain.model.Book;
import org.darkroomlibrary.web.view.DailyCount;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Mapper
public interface BookMapper extends BaseMapper<Book> {

    default Book getById(Integer id) { return selectById(id); }
    default int update(Book book) { return updateById(book); }

    int updateManaged(Book book);

    Book findByIdForUpdate(@Param("id") Integer id);

    List<Book> findByIdsForUpdate(@Param("ids") List<Integer> ids);

    int decreaseAvailableCount(@Param("id") Integer id);

    int increaseAvailableCount(@Param("id") Integer id);

    int increaseStock(@Param("id") Integer id, @Param("count") Integer count);

    int updateCategoryName(@Param("oldName") String oldName, @Param("newName") String newName);

    int countByCategories(@Param("categories") List<String> categories);

    int countByBookshelfIds(@Param("bookshelfIds") List<Integer> bookshelfIds);

    List<Book> query(BookPageQuery dto);

    Integer queryCount(BookPageQuery dto);

    int softDelete(@Param(value = "ids") List<Integer> ids);

    int restore(@Param(value = "ids") List<Integer> ids);

    List<Book> queryLowStock(@Param("threshold") Integer threshold);

    List<Map<String, Object>> categoryStats();

    List<DailyCount> dailyCreateStats(@Param("startTime") LocalDateTime startTime,
                                      @Param("endTime") LocalDateTime endTime);
}
