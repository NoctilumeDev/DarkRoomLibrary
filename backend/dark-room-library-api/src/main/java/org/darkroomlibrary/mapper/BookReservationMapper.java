package org.darkroomlibrary.mapper;

import org.darkroomlibrary.web.dto.query.BookReservationPageQuery;
import org.darkroomlibrary.domain.model.BookReservation;
import org.darkroomlibrary.web.view.BookReservationView;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface BookReservationMapper extends BaseMapper<BookReservation> {

    default BookReservation getById(Integer id) { return selectById(id); }
    default int update(BookReservation entity) { return updateById(entity); }

    List<BookReservationView> query(BookReservationPageQuery dto);

    Integer queryCount(BookReservationPageQuery dto);

    int insertIfNoActiveReservation(BookReservation reservation);

    BookReservation findFirstWaitingByBookId(@Param("bookId") Integer bookId);

    BookReservation findFirstWaitingByBookIdForUpdate(@Param("bookId") Integer bookId);

    List<Integer> findExpiredNotifiedBookIds(@Param("cutoff") LocalDateTime cutoff,
                                             @Param("limit") int limit);

    List<Integer> findBooksNeedingNotification(@Param("limit") int limit);

    BookReservation findNotifiedByBookIdAndUserId(@Param("bookId") Integer bookId,
                                                  @Param("userId") Integer userId);

    int countActiveByBookId(@Param("bookId") Integer bookId);

    int countNotifiedByBookId(@Param("bookId") Integer bookId);

    int countActiveByBookIds(@Param("bookIds") List<Integer> bookIds);

    int countActiveByUserId(@Param("userId") Integer userId);

    int countActiveByUserIds(@Param("userIds") List<Integer> userIds);

    List<Integer> findActiveBookIdsByUserId(@Param("userId") Integer userId);

    int markBorrowed(@Param("id") Integer id);

    int cancelWaiting(@Param("id") Integer id, @Param("userId") Integer userId);

    int markNotified(@Param("id") Integer id, @Param("notifyTime") LocalDateTime notifyTime);

    int expireWaiting(@Param("id") Integer id);

    int expireNotifiedByBookIdBefore(@Param("bookId") Integer bookId,
                                     @Param("cutoff") LocalDateTime cutoff);

    int releaseActiveByUserId(@Param("userId") Integer userId);
}
