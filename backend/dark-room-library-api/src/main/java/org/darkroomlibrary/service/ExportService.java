package org.darkroomlibrary.service;

import org.darkroomlibrary.web.dto.query.BookPageQuery;
import org.darkroomlibrary.web.dto.query.BorrowRecordPageQuery;
import org.darkroomlibrary.web.dto.query.UserPageQuery;

import jakarta.servlet.http.HttpServletResponse;

/**
 * 数据导出服务
 */
public interface ExportService {

    /**
     * 导出借阅记录为 Excel
     */
    void exportBorrowRecords(HttpServletResponse response, BorrowRecordPageQuery dto);

    /**
     * 导出图书信息为 Excel
     */
    void exportBooks(HttpServletResponse response, BookPageQuery dto);

    /**
     * 导出用户信息为 Excel
     */
    void exportUsers(HttpServletResponse response, UserPageQuery dto);

    /**
     * 导出逾期记录为 Excel
     */
    void exportOverdueRecords(HttpServletResponse response);
}