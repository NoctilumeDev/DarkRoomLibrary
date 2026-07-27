package org.darkroomlibrary.service.impl;

import org.darkroomlibrary.mapper.BookMapper;
import org.darkroomlibrary.mapper.BookReservationMapper;
import org.darkroomlibrary.mapper.BorrowRecordMapper;
import org.darkroomlibrary.mapper.BookshelfMapper;
import org.darkroomlibrary.mapper.CategoryMapper;
import org.darkroomlibrary.mapper.ProcurementOrderMapper;
import org.darkroomlibrary.web.response.ApiResponse;
import org.darkroomlibrary.web.response.PageResponse;
import org.darkroomlibrary.web.dto.query.PageQuery;
import org.darkroomlibrary.web.dto.query.BookPageQuery;
import org.darkroomlibrary.domain.type.FileReferenceType;
import org.darkroomlibrary.domain.model.Book;
import org.darkroomlibrary.domain.model.Bookshelf;
import org.darkroomlibrary.web.view.MetricPoint;
import org.darkroomlibrary.service.BookService;
import org.darkroomlibrary.service.FileStorageService;
import org.darkroomlibrary.service.ReservationWorkflowService;
import org.darkroomlibrary.utils.AnalyticsTimeline;
import org.darkroomlibrary.utils.IdListUtils;
import org.darkroomlibrary.utils.TransactionCallbacks;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.interceptor.TransactionAspectSupport;

import jakarta.annotation.Resource;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

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

    @Resource
    private ProcurementOrderMapper procurementOrderMapper;

    @Resource
    private BookshelfMapper bookshelfMapper;

    @Resource
    private CategoryMapper categoryMapper;

    @Resource
    private ReservationWorkflowService reservationWorkflowService;

    /**
     * 新增图书
     *
     * @param book 图书信息
     * @return ApiResponse<Void>
     */
    @Override
    @Transactional
    public ApiResponse<Void> save(Book book) {
        if (book == null) {
            return ApiResponse.error("图书参数不能为空");
        }
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
        book.setVersion(0);
        book.setCreateTime(LocalDateTime.now());
        book.setIsDeleted(false);
        book.setCover(normalizeOptionalText(book.getCover()));
        String categoryName = normalizeOptionalText(book.getCategory());
        if (categoryName == null) {
            return ApiResponse.error("请选择图书分类");
        }
        if (categoryMapper.findByNameForUpdate(categoryName) == null) {
            return ApiResponse.error("所选分类不存在或已被调整");
        }
        book.setCategory(categoryName);
        if (book.getBookshelfId() != null
                && bookshelfMapper.findByIdForUpdate(book.getBookshelfId()) == null) {
            return ApiResponse.error("所选书架不存在");
        }
        if (bookMapper.insert(book) != 1) {
            return ApiResponse.error("图书新增失败，请重试");
        }
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
        Book snapshot = book == null || book.getId() == null
                ? null : bookMapper.getById(book.getId());
        if (snapshot == null) {
            return ApiResponse.error("图书不存在");
        }
        boolean fullManagementEdit = book.getName() != null || book.getAuthor() != null;
        String targetCategory = fullManagementEdit
                ? normalizeOptionalText(book.getCategory()) : snapshot.getCategory();
        if (fullManagementEdit) {
            if (targetCategory == null) {
                return ApiResponse.error("请选择图书分类");
            }
            if (categoryMapper.findByNameForUpdate(targetCategory) == null) {
                return ApiResponse.error("所选分类不存在或已被调整");
            }
        }
        Integer targetBookshelfId = fullManagementEdit
                ? book.getBookshelfId() : snapshot.getBookshelfId();
        List<Bookshelf> lockedShelves = lockBookshelves(snapshot.getBookshelfId(), targetBookshelfId);
        if (targetBookshelfId != null && lockedShelves.stream()
                .noneMatch(shelf -> Objects.equals(shelf.getId(), targetBookshelfId))) {
            return ApiResponse.error("所选书架不存在");
        }
        Book existing = bookMapper.findByIdForUpdate(book.getId());
        if (existing == null) {
            return ApiResponse.error("图书不存在");
        }
        if (book.getVersion() == null) {
            return ApiResponse.error("图书版本缺失，请刷新后重试");
        }
        if (!Objects.equals(existing.getVersion(), book.getVersion())) {
            return ApiResponse.error("图书信息已被其他操作更新，请刷新后重试");
        }
        if (!Objects.equals(existing.getBookshelfId(), snapshot.getBookshelfId())) {
            return ApiResponse.error("图书书架状态已变化，请刷新后重试");
        }
        boolean updatesStock = book.getTotalCount() != null || book.getAvailableCount() != null;
        if (updatesStock
                && (book.getOriginalTotalCount() == null
                || book.getOriginalAvailableCount() == null)) {
            return ApiResponse.error("库存快照缺失，请刷新后重试");
        }
        if (updatesStock
                && (!Objects.equals(existing.getTotalCount(), book.getOriginalTotalCount())
                || !Objects.equals(existing.getAvailableCount(), book.getOriginalAvailableCount()))) {
            return ApiResponse.error("图书库存已被借还或入库操作更新，请刷新后重试");
        }
        Integer totalCount = book.getTotalCount() == null ? existing.getTotalCount() : book.getTotalCount();
        Integer availableCount = book.getAvailableCount() == null
                ? existing.getAvailableCount() : book.getAvailableCount();
        int activeBorrowCount = borrowRecordMapper.countActiveByBookId(book.getId());
        int notifiedReservationCount = bookReservationMapper.countNotifiedByBookId(book.getId());
        if (totalCount == null || availableCount == null || totalCount < 0 || availableCount < 0) {
            return ApiResponse.error("库存数量不能小于0");
        }
        if (availableCount > totalCount) {
            return ApiResponse.error("可借数量不能大于总数量");
        }
        if (totalCount < activeBorrowCount || availableCount > totalCount - activeBorrowCount) {
            return ApiResponse.error("库存调整与当前在借数量冲突");
        }
        if (availableCount < notifiedReservationCount) {
            return ApiResponse.error("可借库存不能小于已通知预约数量");
        }
        String targetCover = fullManagementEdit
                ? normalizeOptionalText(book.getCover()) : existing.getCover();
        Book update = Book.builder()
                .id(book.getId())
                .name(fullManagementEdit ? book.getName() : existing.getName())
                .author(fullManagementEdit ? book.getAuthor() : existing.getAuthor())
                .isbn(fullManagementEdit ? book.getIsbn() : existing.getIsbn())
                .publisher(fullManagementEdit ? book.getPublisher() : existing.getPublisher())
                .category(fullManagementEdit ? targetCategory : existing.getCategory())
                .totalCount(totalCount)
                .availableCount(availableCount)
                .cover(targetCover)
                .description(fullManagementEdit ? book.getDescription() : existing.getDescription())
                .bookshelfId(targetBookshelfId)
                .version(book.getVersion())
                .build();
        if (bookMapper.updateManaged(update) == 0) {
            return ApiResponse.error("图书状态已变化，请刷新后重试");
        }
        if (!Objects.equals(targetCover, existing.getCover())
                && !fileStorageService.bindSingle(
                targetCover, FileReferenceType.BOOK_COVER, book.getId())) {
            TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
            return ApiResponse.error("封面文件无效或不属于当前用户");
        }
        if (availableCount > existing.getAvailableCount()) {
            notifyReservationsAfterCommit(book.getId());
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
        if (IdListUtils.exceedsBatchLimit(uniqueIds)) {
            return ApiResponse.error("单次最多删除" + IdListUtils.MAX_BATCH_SIZE + "本图书");
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
        if (procurementOrderMapper.countActiveByBookIds(uniqueIds) > 0) {
            return ApiResponse.error("存在进行中的采购单，不能删除相关图书");
        }
        if (bookMapper.softDelete(uniqueIds) != uniqueIds.size()) {
            TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
            return ApiResponse.error("图书状态已变化，请刷新后重试");
        }
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
        if (IdListUtils.exceedsBatchLimit(uniqueIds)) {
            return ApiResponse.error("单次最多恢复" + IdListUtils.MAX_BATCH_SIZE + "本图书");
        }
        if (bookMapper.findByIdsForUpdate(uniqueIds).size() != uniqueIds.size()) {
            return ApiResponse.error("部分图书不存在");
        }
        if (bookMapper.restore(uniqueIds) != uniqueIds.size()) {
            TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
            return ApiResponse.error("图书状态已变化，请刷新后重试");
        }
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
        List<MetricPoint> chartPoints = AnalyticsTimeline.toDailyMetrics(
                day,
                bookMapper.dailyCreateStats(queryDto.getStartTime(), queryDto.getEndTime())
        );
        return ApiResponse.success(chartPoints);
    }

    private List<Integer> normalizeIds(List<Integer> ids) {
        return IdListUtils.normalize(ids);
    }

    private List<Bookshelf> lockBookshelves(Integer currentBookshelfId, Integer targetBookshelfId) {
        List<Integer> ids = new ArrayList<>();
        if (currentBookshelfId != null) {
            ids.add(currentBookshelfId);
        }
        if (targetBookshelfId != null && !ids.contains(targetBookshelfId)) {
            ids.add(targetBookshelfId);
        }
        ids.sort(Integer::compareTo);
        return ids.isEmpty() ? List.of() : bookshelfMapper.findByIdsForUpdate(ids);
    }

    private String normalizeOptionalText(String value) {
        return value == null || value.trim().isEmpty() ? null : value.trim();
    }

    private void notifyReservationsAfterCommit(Integer bookId) {
        Runnable notification = () -> {
            try {
                reservationWorkflowService.onBookReturned(bookId);
            } catch (Exception e) {
                log.warn("库存调整后的预约通知失败，等待定时对账: bookId={}, error={}",
                        bookId, e.getMessage());
            }
        };
        TransactionCallbacks.afterCommit(notification);
    }
}
