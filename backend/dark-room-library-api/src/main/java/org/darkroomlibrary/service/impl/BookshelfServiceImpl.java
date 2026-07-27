package org.darkroomlibrary.service.impl;

import org.darkroomlibrary.mapper.BookMapper;
import org.darkroomlibrary.mapper.BookshelfMapper;
import org.darkroomlibrary.web.response.ApiResponse;
import org.darkroomlibrary.web.response.PageResponse;
import org.darkroomlibrary.web.dto.query.BookshelfPageQuery;
import org.darkroomlibrary.domain.model.Bookshelf;
import org.darkroomlibrary.service.BookshelfService;
import org.darkroomlibrary.utils.IdListUtils;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.interceptor.TransactionAspectSupport;

import jakarta.annotation.Resource;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 书架服务实现
 */
@Service
public class BookshelfServiceImpl implements BookshelfService {

    @Resource
    private BookshelfMapper bookshelfMapper;

    @Resource
    private BookMapper bookMapper;

    @Override
    @Transactional
    public ApiResponse<Void> save(Bookshelf bookshelf) {
        String validationError = validate(bookshelf, false);
        if (validationError != null) {
            return ApiResponse.error(validationError);
        }
        if (bookshelf.getCapacity() == null) {
            bookshelf.setCapacity(100);
        }
        normalizeText(bookshelf);
        bookshelf.setCreateTime(LocalDateTime.now());
        try {
            if (bookshelfMapper.insert(bookshelf) != 1) {
                return ApiResponse.error("书架新增失败，请重试");
            }
        } catch (DuplicateKeyException e) {
            return ApiResponse.error("书架名称已存在");
        }
        return ApiResponse.success("新增书架成功");
    }

    @Override
    @Transactional
    public ApiResponse<Void> update(Bookshelf bookshelf) {
        String validationError = validate(bookshelf, true);
        if (validationError != null) {
            return ApiResponse.error(validationError);
        }
        if (bookshelfMapper.findByIdForUpdate(bookshelf.getId()) == null) {
            return ApiResponse.error("书架不存在");
        }
        normalizeText(bookshelf);
        try {
            if (bookshelfMapper.update(bookshelf) == 0) {
                return ApiResponse.error("书架状态已变化，请刷新后重试");
            }
        } catch (DuplicateKeyException e) {
            return ApiResponse.error("书架名称已存在");
        }
        return ApiResponse.success("修改书架成功");
    }

    @Override
    @Transactional
    public ApiResponse<Void> batchDelete(List<Integer> ids) {
        List<Integer> normalizedIds = IdListUtils.normalize(ids);
        if (normalizedIds.isEmpty()) {
            return ApiResponse.error("请选择要删除的书架");
        }
        if (IdListUtils.exceedsBatchLimit(normalizedIds)) {
            return ApiResponse.error("单次最多删除" + IdListUtils.MAX_BATCH_SIZE + "个书架");
        }
        if (bookshelfMapper.findByIdsForUpdate(normalizedIds).size() != normalizedIds.size()) {
            return ApiResponse.error("部分书架不存在");
        }
        if (bookMapper.countByBookshelfIds(normalizedIds) > 0) {
            return ApiResponse.error("书架仍被图书使用，请先调整相关图书");
        }
        if (bookshelfMapper.batchDelete(normalizedIds) != normalizedIds.size()) {
            TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
            return ApiResponse.error("书架状态已变化，请刷新后重试");
        }
        return ApiResponse.success("删除书架成功");
    }

    @Override
    public ApiResponse<List<Bookshelf>> query(BookshelfPageQuery dto) {
        List<Bookshelf> list = bookshelfMapper.query(dto);
        Integer total = bookshelfMapper.queryCount(dto);
        return PageResponse.success(list, total);
    }

    @Override
    public ApiResponse<List<Bookshelf>> queryAll() {
        List<Bookshelf> list = bookshelfMapper.queryAll();
        return ApiResponse.success(list);
    }

    private String validate(Bookshelf bookshelf, boolean requireId) {
        if (bookshelf == null) {
            return "书架参数不能为空";
        }
        if (requireId && bookshelf.getId() == null) {
            return "书架编号不能为空";
        }
        if (bookshelf.getName() == null || bookshelf.getName().trim().isEmpty()) {
            return "书架名称不能为空";
        }
        if (bookshelf.getName().trim().length() > 50) {
            return "书架名称不能超过50个字符";
        }
        if (bookshelf.getLocation() != null && bookshelf.getLocation().trim().length() > 100) {
            return "书架位置不能超过100个字符";
        }
        if (bookshelf.getDescription() != null && bookshelf.getDescription().trim().length() > 500) {
            return "书架说明不能超过500个字符";
        }
        if (bookshelf.getCapacity() != null && bookshelf.getCapacity() <= 0) {
            return "书架容量必须大于0";
        }
        return null;
    }

    private void normalizeText(Bookshelf bookshelf) {
        bookshelf.setName(bookshelf.getName().trim());
        if (bookshelf.getLocation() != null) {
            bookshelf.setLocation(bookshelf.getLocation().trim());
        }
        if (bookshelf.getDescription() != null) {
            bookshelf.setDescription(bookshelf.getDescription().trim());
        }
    }
}
