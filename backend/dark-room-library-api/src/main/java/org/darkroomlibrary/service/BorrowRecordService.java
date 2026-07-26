package org.darkroomlibrary.service;

import org.darkroomlibrary.web.response.ApiResponse;
import org.darkroomlibrary.web.dto.query.BorrowRecordPageQuery;
import org.darkroomlibrary.web.view.BorrowRecordView;

import java.util.List;

/**
 * 借阅记录服务接口
 */
public interface BorrowRecordService {

    /**
     * 借书
     *
     * @param bookId 图书ID
     * @return ApiResponse<Void>
     */
    ApiResponse<Void> borrow(Integer bookId);

    /**
     * 还书
     *
     * @param recordId 记录ID
     * @return ApiResponse<Void>
     */
    ApiResponse<Void> returnBook(Integer recordId);

    /**
     * 续借
     *
     * @param recordId 记录ID
     * @return ApiResponse<Void>
     */
    ApiResponse<Void> renew(Integer recordId);

    /**
     * 分页查询借阅记录
     *
     * @param dto 查询参数
     * @return ApiResponse<List<BorrowRecordView>>
     */
    ApiResponse<List<BorrowRecordView>> query(BorrowRecordPageQuery dto);
}
