package org.darkroomlibrary;

import org.darkroomlibrary.context.CurrentUserContext;
import org.darkroomlibrary.mapper.BookMapper;
import org.darkroomlibrary.mapper.BorrowRecordMapper;
import org.darkroomlibrary.mapper.UserMapper;
import org.darkroomlibrary.domain.model.Book;
import org.darkroomlibrary.domain.model.BorrowRecord;
import org.darkroomlibrary.domain.model.User;
import org.darkroomlibrary.domain.type.AccountStatus;
import org.darkroomlibrary.domain.type.LoginStatus;
import org.darkroomlibrary.domain.type.UserRole;
import org.darkroomlibrary.domain.type.MuteStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

import jakarta.annotation.Resource;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 测试基类，提供通用测试工具方法
 */
@SpringBootTest
@ActiveProfiles("test")
public abstract class BaseTest {

    private static final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    @Resource
    protected UserMapper userMapper;

    @Resource
    protected BookMapper bookMapper;

    @Resource
    protected BorrowRecordMapper borrowRecordMapper;

    /**
     * 创建测试用户
     */
    protected User createTestUser(String account, String userName, String email) {
        User user = User.builder()
                .userAccount(account)
                .userName(userName)
                .userPwd(encoder.encode("Test@123456"))
                .userEmail(email)
                .userRole(UserRole.READER.code())
                .isCoordinatorAdmin(false)
                .accountStatus(AccountStatus.NORMAL.code())
                .isLogin(LoginStatus.ACTIVE.disabled())
                .isWord(MuteStatus.ACTIVE.muted())
                .createTime(LocalDateTime.now())
                .build();
        userMapper.insert(user);
        // useGeneratedKeys=true 会自动回填ID
        return user;
    }

    /**
     * 创建测试图书
     */
    protected Book createTestBook(String name, String author, int totalCount) {
        Book book = Book.builder()
                .name(name)
                .author(author)
                .isbn("9787123456789")
                .publisher("测试出版社")
                .category("测试分类")
                .totalCount(totalCount)
                .availableCount(totalCount)
                .cover("")
                .description("测试图书")
                .createTime(LocalDateTime.now())
                .isDeleted(false)
                .build();
        bookMapper.insert(book);
        return book;
    }

    /**
     * 创建测试借阅记录
     */
    protected BorrowRecord createTestBorrowRecord(Integer userId, Integer bookId, LocalDateTime dueDate) {
        int stockUpdated = bookMapper.decreaseAvailableCount(bookId);
        if (stockUpdated == 0) {
            throw new IllegalStateException("测试借阅记录创建失败：图书库存不足");
        }
        BorrowRecord record = BorrowRecord.builder()
                .userId(userId)
                .bookId(bookId)
                .borrowTime(LocalDateTime.now())
                .dueDate(dueDate)
                .status(false)
                .fineAmount(BigDecimal.ZERO)
                .build();
        borrowRecordMapper.insert(record);
        return record;
    }

    /**
     * 设置当前用户上下文
     */
    protected void setCurrentUser(Integer userId, Integer roleId) {
        CurrentUserContext.bind(userId, roleId);
    }

    /**
     * 清除用户上下文
     */
    protected void clearContext() {
        CurrentUserContext.clear();
    }

    /**
     * 加密密码
     */
    protected String encodePassword(String rawPassword) {
        return encoder.encode(rawPassword);
    }
}
