package org.darkroomlibrary.service.impl;

import org.darkroomlibrary.mapper.BookMapper;
import org.darkroomlibrary.mapper.BorrowRecordMapper;
import org.darkroomlibrary.mapper.UserMapper;
import org.darkroomlibrary.context.CurrentUserContext;
import org.darkroomlibrary.web.dto.query.BookPageQuery;
import org.darkroomlibrary.web.dto.query.BorrowRecordPageQuery;
import org.darkroomlibrary.web.dto.query.UserPageQuery;
import org.darkroomlibrary.domain.type.UserRole;
import org.darkroomlibrary.domain.model.Book;
import org.darkroomlibrary.domain.model.User;
import org.darkroomlibrary.web.view.BorrowRecordView;
import org.darkroomlibrary.service.ExportService;
import com.alibaba.excel.EasyExcel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 数据导出服务实现
 */
@Slf4j
@Service
public class ExportServiceImpl implements ExportService {

    @Resource
    private BookMapper bookMapper;

    @Resource
    private BorrowRecordMapper borrowRecordMapper;

    @Resource
    private UserMapper userMapper;

    private static final DateTimeFormatter DTF = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter FILE_DTF = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    // ==================== 公共模板方法 ====================

    /**
     * 通用导出模板：设置响应头 → 构建数据 → 写入 Excel
     */
    private void export(HttpServletResponse response, String prefix, List<List<String>> data) {
        try {
            String fileName = prefix + "_" + LocalDateTime.now().format(FILE_DTF);
            setExcelResponse(response, fileName);
            EasyExcel.write(response.getOutputStream())
                    .sheet(prefix)
                    .doWrite(data);
        } catch (Exception e) {
            log.error("导出[{}]失败", prefix, e);
            try {
                response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "导出失败");
            } catch (IOException ignored) {
            }
        }
    }

    private void setExcelResponse(HttpServletResponse response, String fileName) throws IOException {
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setCharacterEncoding("UTF-8");
        String encodedFileName = URLEncoder.encode(fileName, StandardCharsets.UTF_8.name()).replace("+", "%20");
        response.setHeader("Content-Disposition", "attachment;filename=" + encodedFileName + ".xlsx");
    }

    private String safeStr(String value) {
        return value != null ? value : "";
    }

    private String safeDate(LocalDateTime dt) {
        return dt != null ? dt.format(DTF) : "";
    }

    private boolean requireAdmin(HttpServletResponse response) {
        Integer roleId = CurrentUserContext.roleCode();
        if (Objects.equals(roleId, UserRole.ADMIN.code())
                || Objects.equals(roleId, UserRole.SUPER_ADMIN.code())) {
            return true;
        }
        try {
            response.sendError(HttpServletResponse.SC_FORBIDDEN, "无导出权限");
        } catch (IOException e) {
            log.warn("导出权限拒绝响应失败", e);
        }
        return false;
    }

    private boolean isSuperAdmin() {
        return Objects.equals(CurrentUserContext.roleCode(), UserRole.SUPER_ADMIN.code());
    }

    private String exportEmail(String email, boolean showFullEmail) {
        if (showFullEmail) {
            return safeStr(email);
        }
        return maskEmail(email);
    }

    private String maskEmail(String email) {
        if (email == null || email.isBlank()) {
            return "";
        }
        int atIndex = email.indexOf('@');
        if (atIndex <= 0) {
            return "***";
        }
        String localPart = email.substring(0, atIndex);
        String domain = email.substring(atIndex);
        if (localPart.length() == 1) {
            return localPart + "***" + domain;
        }
        int keepLength = Math.min(3, localPart.length());
        return localPart.substring(0, keepLength) + "***" + domain;
    }

    // ==================== 导出方法 ====================

    @Override
    public void exportBorrowRecords(HttpServletResponse response, BorrowRecordPageQuery dto) {
        if (!requireAdmin(response)) {
            return;
        }
        List<BorrowRecordView> records = borrowRecordMapper.query(dto);

        List<List<String>> data = new ArrayList<>();
        data.add(List.of("用户名", "图书名", "借阅时间", "应还日期", "归还时间", "状态", "罚款金额"));

        for (BorrowRecordView record : records) {
            data.add(List.of(
                    safeStr(record.getUserName()),
                    safeStr(record.getBookName()),
                    safeDate(record.getBorrowTime()),
                    safeDate(record.getDueDate()),
                    safeDate(record.getReturnTime()),
                    Boolean.TRUE.equals(record.getStatus()) ? "已归还" : "借阅中",
                    record.getFineAmount() != null ? record.getFineAmount().toString() : "0"
            ));
        }
        export(response, "借阅记录", data);
    }

    @Override
    public void exportBooks(HttpServletResponse response, BookPageQuery dto) {
        if (!requireAdmin(response)) {
            return;
        }
        List<Book> books = bookMapper.query(dto);

        List<List<String>> data = new ArrayList<>();
        data.add(List.of("书名", "作者", "ISBN", "出版社", "分类", "总数量", "可借数量"));

        for (Book book : books) {
            data.add(List.of(
                    safeStr(book.getName()),
                    safeStr(book.getAuthor()),
                    safeStr(book.getIsbn()),
                    safeStr(book.getPublisher()),
                    safeStr(book.getCategory()),
                    book.getTotalCount() != null ? book.getTotalCount().toString() : "0",
                    book.getAvailableCount() != null ? book.getAvailableCount().toString() : "0"
            ));
        }
        export(response, "图书信息", data);
    }

    @Override
    public void exportUsers(HttpServletResponse response, UserPageQuery dto) {
        if (!requireAdmin(response)) {
            return;
        }
        if (dto == null) {
            dto = new UserPageQuery();
        }
        List<User> users = userMapper.query(dto);
        boolean showFullEmail = isSuperAdmin();

        List<List<String>> data = new ArrayList<>();
        data.add(List.of("账号", "昵称", "邮箱", "角色", "注册时间", "状态"));

        for (User user : users) {
            String roleName = UserRole.displayNameOf(user.getUserRole());
            data.add(List.of(
                    safeStr(user.getUserAccount()),
                    safeStr(user.getUserName()),
                    exportEmail(user.getUserEmail(), showFullEmail),
                    roleName != null ? roleName : "未知",
                    safeDate(user.getCreateTime()),
                    Boolean.TRUE.equals(user.getIsLogin()) ? "已禁用" : "正常"
            ));
        }
        export(response, "用户信息", data);
    }

    @Override
    public void exportOverdueRecords(HttpServletResponse response) {
        if (!requireAdmin(response)) {
            return;
        }
        BorrowRecordPageQuery dto = new BorrowRecordPageQuery();
        List<BorrowRecordView> allRecords = borrowRecordMapper.query(dto);

        List<List<String>> data = new ArrayList<>();
        data.add(List.of("用户名", "书名", "借阅时间", "应还日期", "逾期天数", "罚款金额"));

        LocalDateTime now = LocalDateTime.now();
        for (BorrowRecordView record : allRecords) {
            if (Boolean.TRUE.equals(record.getStatus())) continue;
            if (record.getDueDate() == null || !now.isAfter(record.getDueDate())) continue;

            long overdueDays = ChronoUnit.DAYS.between(record.getDueDate(), now);
            data.add(List.of(
                    safeStr(record.getUserName()),
                    safeStr(record.getBookName()),
                    safeDate(record.getBorrowTime()),
                    safeDate(record.getDueDate()),
                    String.valueOf(overdueDays),
                    record.getFineAmount() != null ? record.getFineAmount().toString() : "0"
            ));
        }
        export(response, "逾期记录", data);
    }
}
