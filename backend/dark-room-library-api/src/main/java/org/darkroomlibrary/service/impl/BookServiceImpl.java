package org.darkroomlibrary.service.impl;

import org.darkroomlibrary.mapper.BookMapper;
import org.darkroomlibrary.mapper.BookReservationMapper;
import org.darkroomlibrary.mapper.BorrowRecordMapper;
import org.darkroomlibrary.web.response.ApiResponse;
import org.darkroomlibrary.web.response.PageResponse;
import org.darkroomlibrary.web.dto.query.PageQuery;
import org.darkroomlibrary.web.dto.query.BookPageQuery;
import org.darkroomlibrary.domain.type.FileReferenceType;
import org.darkroomlibrary.domain.model.Book;
import org.darkroomlibrary.web.view.MetricPoint;
import org.darkroomlibrary.service.BookService;
import org.darkroomlibrary.service.FileStorageService;
import org.darkroomlibrary.utils.AnalyticsTimeline;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.interceptor.TransactionAspectSupport;

import jakarta.annotation.Resource;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.LinkedHashSet;
import java.util.stream.Collectors;

/**
 * 图书业务逻辑实现
 */
@Slf4j
@Service
public class BookServiceImpl implements BookService {

    @Resource
    private BookMapper bookMapper;

    @Resource
    private FileStorageService fileStorageService;

    @Resource
    private BorrowRecordMapper borrowRecordMapper;

    @Resource
    private BookReservationMapper bookReservationMapper;

    /**
     * 新增图书
     *
     * @param book 图书信息
     * @return ApiResponse<Void>
     */
    @Override
    @Transactional
    public ApiResponse<Void> save(Book book) {
        Integer totalCount = book.getTotalCount() == null ? 1 : book.getTotalCount();
        Integer availableCount = book.getAvailableCount() == null ? totalCount : book.getAvailableCount();
        if (totalCount < 0 || availableCount < 0) {
            return ApiResponse.error("库存数量不能小于0");
        }
        if (availableCount > totalCount) {
            return ApiResponse.error("可借数量不能大于总数量");
        }
        book.setTotalCount(totalCount);
        book.setAvailableCount(availableCount);
        book.setId(null);
        book.setCreateTime(LocalDateTime.now());
        book.setIsDeleted(false);
        bookMapper.insert(book);
        if (!fileStorageService.bindSingle(book.getCover(), FileReferenceType.BOOK_COVER, book.getId())) {
            TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
            return ApiResponse.error("封面文件无效或不属于当前用户");
        }
        return ApiResponse.success();
    }

    /**
     * 修改图书（MyBatis XML 动态 SQL 自动处理 null 字段，无需手动逐字段拷贝）
     *
     * @param book 图书信息
     * @return ApiResponse<Void>
     */
    @Override
    @Transactional
    public ApiResponse<Void> update(Book book) {
        // 校验图书存在
        Book existing = book == null || book.getId() == null
                ? null : bookMapper.findByIdForUpdate(book.getId());
        if (existing == null) {
            return ApiResponse.error("图书不存在");
        }
        Integer totalCount = book.getTotalCount() == null ? existing.getTotalCount() : book.getTotalCount();
        Integer availableCount = book.getAvailableCount() == null
                ? existing.getAvailableCount() : book.getAvailableCount();
        int activeBorrowCount = borrowRecordMapper.countActiveByBookId(book.getId());
        if (totalCount == null || availableCount == null || totalCount < 0 || availableCount < 0) {
            return ApiResponse.error("库存数量不能小于0");
        }
        if (availableCount > totalCount) {
            return ApiResponse.error("可借数量不能大于总数量");
        }
        if (totalCount < activeBorrowCount || availableCount > totalCount - activeBorrowCount) {
            return ApiResponse.error("库存调整与当前在借数量冲突");
        }
        Book update = Book.builder()
                .id(book.getId())
                .name(book.getName())
                .author(book.getAuthor())
                .isbn(book.getIsbn())
                .publisher(book.getPublisher())
                .category(book.getCategory())
                .totalCount(totalCount)
                .availableCount(availableCount)
                .cover(book.getCover())
                .description(book.getDescription())
                .bookshelfId(book.getBookshelfId())
                .build();
        bookMapper.update(update);
        if (book.getCover() != null && !Objects.equals(book.getCover(), existing.getCover())
                && !fileStorageService.bindSingle(
                book.getCover(), FileReferenceType.BOOK_COVER, book.getId())) {
            TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
            return ApiResponse.error("封面文件无效或不属于当前用户");
        }
        return ApiResponse.success();
    }

    /**
     * 批量删除图书（软删除）
     *
     * @param ids 图书ID集合
     * @return ApiResponse<Void>
     */
    @Override
    @Transactional
    public ApiResponse<Void> batchDelete(List<Integer> ids) {
        List<Integer> uniqueIds = normalizeIds(ids);
        if (uniqueIds.isEmpty()) {
            return ApiResponse.error("请选择要删除的图书");
        }
        if (bookMapper.findByIdsForUpdate(uniqueIds).size() != uniqueIds.size()) {
            return ApiResponse.error("部分图书不存在");
        }
        if (borrowRecordMapper.countActiveByBookIds(uniqueIds) > 0) {
            return ApiResponse.error("存在未归还记录，不能删除相关图书");
        }
        if (bookReservationMapper.countActiveByBookIds(uniqueIds) > 0) {
            return ApiResponse.error("存在进行中的预约，不能删除相关图书");
        }
        bookMapper.softDelete(uniqueIds);
        return ApiResponse.success();
    }

    /**
     * 恢复已删除的图书
     */
    @Override
    @Transactional
    public ApiResponse<Void> restore(List<Integer> ids) {
        List<Integer> uniqueIds = normalizeIds(ids);
        if (uniqueIds.isEmpty()) {
            return ApiResponse.error("请选择要恢复的图书");
        }
        bookMapper.restore(uniqueIds);
        return ApiResponse.success();
    }

    /**
     * 分页查询图书
     *
     * @param dto 查询参数
     * @return ApiResponse<List<Book>>
     */
    @Override
    public ApiResponse<List<Book>> query(BookPageQuery dto) {
        List<Book> bookList = bookMapper.query(dto);
        Integer totalCount = bookMapper.queryCount(dto);
        return PageResponse.success(bookList, totalCount);
    }

    @Override
    public ApiResponse<List<MetricPoint>> queryByDays(Integer day) {
        PageQuery queryDto = AnalyticsTimeline.queryWindow(day);
        BookPageQuery bookPageQuery = new BookPageQuery();
        bookPageQuery.setStartTime(queryDto.getStartTime());
        bookPageQuery.setEndTime(queryDto.getEndTime());
        List<Book> bookList = bookMapper.query(bookPageQuery);
        List<LocalDateTime> localDateTimes = bookList.stream().map(Book::getCreateTime).collect(Collectors.toList());
        List<MetricPoint> chartPoints = AnalyticsTimeline.toDailyMetrics(day, localDateTimes);
        return ApiResponse.success(chartPoints);
    }

    private List<Integer> normalizeIds(List<Integer> ids) {
        if (ids == null) {
            return List.of();
        }
        return ids.stream()
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new))
                .stream()
                .sorted()
                .collect(Collectors.toList());
    }
}
