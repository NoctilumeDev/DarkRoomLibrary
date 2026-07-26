package org.darkroomlibrary.service.impl;

import org.darkroomlibrary.BaseTest;
import org.darkroomlibrary.mapper.BookReservationMapper;
import org.darkroomlibrary.mapper.OperationLogMapper;
import org.darkroomlibrary.web.response.ApiResponse;
import org.darkroomlibrary.web.dto.query.UserPageQuery;
import org.darkroomlibrary.web.dto.command.UserAdminUpdateDto;
import org.darkroomlibrary.web.dto.command.UserRegisterDto;
import org.darkroomlibrary.domain.type.AccountStatus;
import org.darkroomlibrary.domain.type.UserRole;
import org.darkroomlibrary.domain.model.Book;
import org.darkroomlibrary.domain.model.BookReservation;
import org.darkroomlibrary.domain.model.BorrowRecord;
import org.darkroomlibrary.domain.model.OperationLog;
import org.darkroomlibrary.domain.model.User;
import org.darkroomlibrary.service.BorrowRecordService;
import org.darkroomlibrary.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.annotation.DirtiesContext;

import jakarta.annotation.Resource;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
public class UserServiceSecurityTest extends BaseTest {

    @Resource
    private UserService userService;

    @Resource
    private BorrowRecordService borrowRecordService;

    @Resource
    private BookReservationMapper bookReservationMapper;

    @Resource
    private OperationLogMapper operationLogMapper;

    @BeforeEach
    void setUp() {
        clearContext();
    }

    @Test
    @DisplayName("backUpdate rejects normal admin updating super admin")
    void testBackUpdateRejectsAdminUpdatingSuperAdmin() {
        User admin = createTestUserWithRole("security_admin_001", "admin", "security_admin_001@test.com", UserRole.ADMIN.code());
        User superAdmin = createTestUserWithRole("security_super_001", "super", "security_super_001@test.com", UserRole.SUPER_ADMIN.code());
        setCurrentUser(admin.getId(), admin.getUserRole());

        UserAdminUpdateDto dto = UserAdminUpdateDto.builder()
                .id(superAdmin.getId())
                .userName("newSuperName")
                .build();

        ApiResponse<String> result = userService.backUpdate(dto);

        assertNotNull(result);
        assertEquals(400, result.getCode());
    }

    @Test
    @DisplayName("backUpdate rejects disabling current user")
    void testBackUpdateRejectsSelfDisable() {
        User admin = createTestUserWithRole("security_admin_002", "admin2", "security_admin_002@test.com", UserRole.ADMIN.code());
        setCurrentUser(admin.getId(), admin.getUserRole());

        UserAdminUpdateDto dto = UserAdminUpdateDto.builder()
                .id(admin.getId())
                .isLogin(true)
                .build();

        ApiResponse<String> result = userService.backUpdate(dto);

        assertNotNull(result);
        assertEquals(400, result.getCode());
    }

    @Test
    @DisplayName("backUpdate rejects admin updating another admin")
    void testBackUpdateRejectsAdminUpdatingAdmin() {
        User admin1 = createTestUserWithRole("security_admin_003", "admin3", "security_admin_003@test.com", UserRole.ADMIN.code());
        User admin2 = createTestUserWithRole("security_admin_004", "admin4", "security_admin_004@test.com", UserRole.ADMIN.code());
        setCurrentUser(admin1.getId(), admin1.getUserRole());

        UserAdminUpdateDto dto = UserAdminUpdateDto.builder()
                .id(admin2.getId())
                .userName("newAdminName")
                .build();

        ApiResponse<String> result = userService.backUpdate(dto);

        assertNotNull(result);
        assertEquals(400, result.getCode());
    }

    @Test
    @DisplayName("insert rejects normal admin creating privileged account")
    void testInsertRejectsAdminCreatingPrivilegedAccount() {
        User admin = createTestUserWithRole("security_admin_005", "admin5", "security_admin_005@test.com", UserRole.ADMIN.code());
        setCurrentUser(admin.getId(), admin.getUserRole());

        UserRegisterDto dto = newUserRegisterDto("security_new_admin", "新管理员", "security_new_admin@test.com");
        dto.setUserRole(UserRole.ADMIN.code());

        ApiResponse<String> result = userService.insert(dto);

        assertNotNull(result);
        assertEquals(400, result.getCode());
    }

    @Test
    @DisplayName("insert allows super admin creating purchaser")
    void testInsertAllowsSuperAdminCreatingPurchaser() {
        User superAdmin = createTestUserWithRole("security_super_002", "super2", "security_super_002@test.com", UserRole.SUPER_ADMIN.code());
        setCurrentUser(superAdmin.getId(), superAdmin.getUserRole());

        UserRegisterDto dto = newUserRegisterDto("security_buyer_001", "采购员一号", "security_buyer_001@test.com");
        dto.setUserRole(UserRole.ACQUISITIONS.code());

        ApiResponse<String> result = userService.insert(dto);

        assertNotNull(result);
        assertEquals(200, result.getCode());
        User saved = userMapper.getByActive(User.builder().userAccount("security_buyer_001").build());
        assertNotNull(saved);
        assertEquals(UserRole.ACQUISITIONS.code(), saved.getUserRole());
    }

    @Test
    @DisplayName("insert allows super admin creating coordinator admin")
    void testInsertAllowsSuperAdminCreatingCoordinatorAdmin() {
        User superAdmin = createTestUserWithRole("security_super_004", "super4", "security_super_004@test.com", UserRole.SUPER_ADMIN.code());
        setCurrentUser(superAdmin.getId(), superAdmin.getUserRole());

        UserRegisterDto dto = newUserRegisterDto("security_coordinator_001", "馆务协调员1", "security_coordinator_001@test.com");
        dto.setUserRole(UserRole.ADMIN.code());
        dto.setIsCoordinatorAdmin(true);

        ApiResponse<String> result = userService.insert(dto);

        assertNotNull(result);
        assertEquals(200, result.getCode());
        User saved = userMapper.getByActive(User.builder().userAccount("security_coordinator_001").build());
        assertNotNull(saved);
        assertEquals(UserRole.ADMIN.code(), saved.getUserRole());
        assertTrue(saved.getIsCoordinatorAdmin());
    }

    @Test
    @DisplayName("backUpdate rejects normal admin promoting reader")
    void testBackUpdateRejectsAdminPromotingReader() {
        User admin = createTestUserWithRole("security_admin_006", "admin6", "security_admin_006@test.com", UserRole.ADMIN.code());
        User reader = createTestUserWithRole("security_reader_001", "reader1", "security_reader_001@test.com", UserRole.READER.code());
        setCurrentUser(admin.getId(), admin.getUserRole());

        UserAdminUpdateDto dto = UserAdminUpdateDto.builder()
                .id(reader.getId())
                .userRole(UserRole.SUPER_ADMIN.code())
                .build();

        ApiResponse<String> result = userService.backUpdate(dto);

        assertNotNull(result);
        assertEquals(400, result.getCode());
        User after = userMapper.getByActive(User.builder().id(reader.getId()).build());
        assertEquals(UserRole.READER.code(), after.getUserRole());
    }

    @Test
    @DisplayName("backUpdate allows super admin appointing and revoking coordinator admin")
    void testBackUpdateAllowsSuperAdminManageCoordinatorAdmin() {
        User superAdmin = createTestUserWithRole("security_super_005", "super5", "security_super_005@test.com", UserRole.SUPER_ADMIN.code());
        User admin = createTestUserWithRole("security_admin_013", "admin13", "security_admin_013@test.com", UserRole.ADMIN.code());
        setCurrentUser(superAdmin.getId(), superAdmin.getUserRole());

        UserAdminUpdateDto appoint = UserAdminUpdateDto.builder()
                .id(admin.getId())
                .isCoordinatorAdmin(true)
                .build();
        assertEquals(200, userService.backUpdate(appoint).getCode());
        User appointed = userMapper.getByActive(User.builder().id(admin.getId()).build());
        assertTrue(appointed.getIsCoordinatorAdmin());

        UserAdminUpdateDto revoke = UserAdminUpdateDto.builder()
                .id(admin.getId())
                .isCoordinatorAdmin(false)
                .build();
        assertEquals(200, userService.backUpdate(revoke).getCode());
        User revoked = userMapper.getByActive(User.builder().id(admin.getId()).build());
        assertFalse(revoked.getIsCoordinatorAdmin());
    }

    @Test
    @DisplayName("backUpdate rejects normal admin appointing coordinator admin")
    void testBackUpdateRejectsAdminAppointingCoordinatorAdmin() {
        User admin = createTestUserWithRole("security_admin_014", "admin14", "security_admin_014@test.com", UserRole.ADMIN.code());
        User reader = createTestUserWithRole("security_reader_003", "reader3", "security_reader_003@test.com", UserRole.READER.code());
        setCurrentUser(admin.getId(), admin.getUserRole());

        UserAdminUpdateDto dto = UserAdminUpdateDto.builder()
                .id(reader.getId())
                .isCoordinatorAdmin(true)
                .build();

        ApiResponse<String> result = userService.backUpdate(dto);

        assertNotNull(result);
        assertEquals(400, result.getCode());
    }

    @Test
    @DisplayName("backUpdate allows super admin resetting another user's password")
    void testBackUpdateAllowsSuperAdminResettingPassword() {
        User superAdmin = createTestUserWithRole("security_super_pwd", "superPwd", "super_pwd@test.com", UserRole.SUPER_ADMIN.code());
        User reader = createTestUserWithRole("security_reader_pwd", "readerPwd", "reader_pwd@test.com", UserRole.READER.code());
        setCurrentUser(superAdmin.getId(), superAdmin.getUserRole());

        ApiResponse<String> result = userService.backUpdate(UserAdminUpdateDto.builder()
                .id(reader.getId())
                .userPwd("Reset@123")
                .build());

        assertEquals(200, result.getCode());
        User after = userMapper.getByActive(User.builder().id(reader.getId()).build());
        assertTrue(new BCryptPasswordEncoder().matches("Reset@123", after.getUserPwd()));
    }

    @Test
    @DisplayName("backUpdate rejects normal admin resetting another user's password")
    void testBackUpdateRejectsAdminResettingPassword() {
        User admin = createTestUserWithRole("security_admin_pwd", "adminPwd", "admin_pwd@test.com", UserRole.ADMIN.code());
        User reader = createTestUserWithRole("security_reader_pwd2", "readerPwd2", "reader_pwd2@test.com", UserRole.READER.code());
        setCurrentUser(admin.getId(), admin.getUserRole());

        ApiResponse<String> result = userService.backUpdate(UserAdminUpdateDto.builder()
                .id(reader.getId())
                .userPwd("Reset@123")
                .build());

        assertEquals(400, result.getCode());
        User after = userMapper.getByActive(User.builder().id(reader.getId()).build());
        assertFalse(new BCryptPasswordEncoder().matches("Reset@123", after.getUserPwd()));
    }

    @Test
    @DisplayName("backUpdate clears coordinator admin when super admin changes role away from admin")
    void testBackUpdateClearsCoordinatorAdminWhenRoleChanged() {
        User superAdmin = createTestUserWithRole("security_super_006", "super6", "security_super_006@test.com", UserRole.SUPER_ADMIN.code());
        User admin = createTestUserWithRole("security_admin_015", "admin15", "security_admin_015@test.com", UserRole.ADMIN.code());
        userMapper.update(User.builder().id(admin.getId()).isCoordinatorAdmin(true).build());
        setCurrentUser(superAdmin.getId(), superAdmin.getUserRole());

        UserAdminUpdateDto dto = UserAdminUpdateDto.builder()
                .id(admin.getId())
                .userRole(UserRole.ACQUISITIONS.code())
                .build();

        ApiResponse<String> result = userService.backUpdate(dto);

        assertNotNull(result);
        assertEquals(200, result.getCode());
        User after = userMapper.getByActive(User.builder().id(admin.getId()).build());
        assertEquals(UserRole.ACQUISITIONS.code(), after.getUserRole());
        assertFalse(after.getIsCoordinatorAdmin());
    }

    @Test
    @DisplayName("freeze rejects normal admin freezing another admin")
    void testFreezeRejectsAdminFreezingAnotherAdmin() {
        User admin1 = createTestUserWithRole("security_admin_007", "admin7", "security_admin_007@test.com", UserRole.ADMIN.code());
        User admin2 = createTestUserWithRole("security_admin_008", "admin8", "security_admin_008@test.com", UserRole.ADMIN.code());
        setCurrentUser(admin1.getId(), admin1.getUserRole());

        ApiResponse<String> result = userService.freezeUser(admin2.getId());

        assertNotNull(result);
        assertEquals(400, result.getCode());
    }

    @Test
    @DisplayName("unfreeze rejects normal admin unfreezing purchaser")
    void testUnfreezeRejectsAdminUnfreezingPurchaser() {
        User admin = createTestUserWithRole("security_admin_009", "admin9", "security_admin_009@test.com", UserRole.ADMIN.code());
        User buyer = createTestUserWithRole("security_buyer_002", "buyer2", "security_buyer_002@test.com", UserRole.ACQUISITIONS.code());
        userMapper.update(User.builder().id(buyer.getId()).isLogin(true).build());
        setCurrentUser(admin.getId(), admin.getUserRole());

        ApiResponse<String> result = userService.unfreezeUser(buyer.getId());

        assertNotNull(result);
        assertEquals(400, result.getCode());
    }

    @Test
    @DisplayName("query masks non-reader email for normal admin")
    void testQueryMasksNonReaderEmailForAdmin() {
        User admin = createTestUserWithRole("security_admin_010", "admin10", "security_admin_010@test.com", UserRole.ADMIN.code());
        User otherAdmin = createTestUserWithRole("security_admin_011", "admin11", "security_admin_011@test.com", UserRole.ADMIN.code());
        User reader = createTestUserWithRole("security_reader_002", "reader2", "security_reader_002@test.com", UserRole.READER.code());
        setCurrentUser(admin.getId(), admin.getUserRole());

        ApiResponse<java.util.List<User>> result = userService.query(new UserPageQuery());

        assertNotNull(result);
        assertEquals(200, result.getCode());
        User maskedAdmin = result.getData().stream()
                .filter(item -> otherAdmin.getId().equals(item.getId()))
                .findFirst()
                .orElseThrow();
        User visibleReader = result.getData().stream()
                .filter(item -> reader.getId().equals(item.getId()))
                .findFirst()
                .orElseThrow();
        assertTrue(maskedAdmin.getUserEmail().contains("***"));
        assertFalse(maskedAdmin.getUserEmail().equalsIgnoreCase("security_admin_011@test.com"));
        assertEquals("security_reader_002@test.com", visibleReader.getUserEmail());
    }

    @Test
    @DisplayName("query shows full email for super admin")
    void testQueryShowsFullEmailForSuperAdmin() {
        User superAdmin = createTestUserWithRole("security_super_003", "super3", "security_super_003@test.com", UserRole.SUPER_ADMIN.code());
        User admin = createTestUserWithRole("security_admin_012", "admin12", "security_admin_012@test.com", UserRole.ADMIN.code());
        setCurrentUser(superAdmin.getId(), superAdmin.getUserRole());

        ApiResponse<java.util.List<User>> result = userService.query(new UserPageQuery());

        assertNotNull(result);
        assertEquals(200, result.getCode());
        User visibleAdmin = result.getData().stream()
                .filter(item -> admin.getId().equals(item.getId()))
                .findFirst()
                .orElseThrow();
        assertEquals("security_admin_012@test.com", visibleAdmin.getUserEmail());
    }

    @Test
    @DisplayName("cancelAccount allows reader without unfinished business")
    void testCancelAccountAllowsReaderWithoutUnfinishedBusiness() {
        User reader = createTestUserWithRole("security_cancel_reader_001", "cancelReader1", "cancel_reader_001@test.com", UserRole.READER.code());
        setCurrentUser(reader.getId(), reader.getUserRole());

        ApiResponse<String> result = userService.cancelAccount();

        assertNotNull(result);
        assertEquals(200, result.getCode());
        User after = userMapper.getByActive(User.builder().id(reader.getId()).build());
        assertEquals(AccountStatus.CANCELLED.code(), after.getAccountStatus());
        assertTrue(after.getIsLogin());
    }

    @Test
    @DisplayName("cancelAccount rejects non-reader roles")
    void testCancelAccountRejectsNonReaderRole() {
        User admin = createTestUserWithRole("security_cancel_admin_001", "cancelAdmin1", "cancel_admin_001@test.com", UserRole.ADMIN.code());
        setCurrentUser(admin.getId(), admin.getUserRole());

        ApiResponse<String> result = userService.cancelAccount();

        assertNotNull(result);
        assertEquals(400, result.getCode());
        User after = userMapper.getByActive(User.builder().id(admin.getId()).build());
        assertEquals(AccountStatus.NORMAL.code(), after.getAccountStatus());
        assertFalse(after.getIsLogin());
    }

    @Test
    @DisplayName("cancelAccount rejects reader with active borrow")
    void testCancelAccountRejectsActiveBorrow() {
        User reader = createTestUserWithRole("security_cancel_reader_002", "cancelReader2", "cancel_reader_002@test.com", UserRole.READER.code());
        Book book = createTestBook("注销测试借阅书", "测试作者", 1);
        createTestBorrowRecord(reader.getId(), book.getId(), LocalDateTime.now().plusDays(7));
        setCurrentUser(reader.getId(), reader.getUserRole());

        ApiResponse<String> result = userService.cancelAccount();

        assertNotNull(result);
        assertEquals(400, result.getCode());
        User after = userMapper.getByActive(User.builder().id(reader.getId()).build());
        assertEquals(AccountStatus.NORMAL.code(), after.getAccountStatus());
    }

    @Test
    @DisplayName("cancelAccount rejects reader with active reservation")
    void testCancelAccountRejectsActiveReservation() {
        User reader = createTestUserWithRole("security_cancel_reader_003", "cancelReader3", "cancel_reader_003@test.com", UserRole.READER.code());
        Book book = createTestBook("注销测试预约书", "测试作者", 1);
        bookReservationMapper.insert(BookReservation.builder()
                .userId(reader.getId())
                .bookId(book.getId())
                .reserveTime(LocalDateTime.now())
                .status(0)
                .build());
        setCurrentUser(reader.getId(), reader.getUserRole());

        ApiResponse<String> result = userService.cancelAccount();

        assertNotNull(result);
        assertEquals(400, result.getCode());
        User after = userMapper.getByActive(User.builder().id(reader.getId()).build());
        assertEquals(AccountStatus.NORMAL.code(), after.getAccountStatus());
    }

    @Test
    @DisplayName("cancelAccount rejects reader with positive fine")
    void testCancelAccountRejectsPositiveFine() {
        User reader = createTestUserWithRole("security_cancel_reader_004", "cancelReader4", "cancel_reader_004@test.com", UserRole.READER.code());
        Book book = createTestBook("注销测试罚款书", "测试作者", 1);
        BorrowRecord record = createTestBorrowRecord(reader.getId(), book.getId(), LocalDateTime.now().minusDays(2));
        borrowRecordMapper.updateById(BorrowRecord.builder()
                .id(record.getId())
                .status(true)
                .returnTime(LocalDateTime.now())
                .fineAmount(new BigDecimal("1.20"))
                .build());
        setCurrentUser(reader.getId(), reader.getUserRole());

        ApiResponse<String> result = userService.cancelAccount();

        assertNotNull(result);
        assertEquals(400, result.getCode());
        User after = userMapper.getByActive(User.builder().id(reader.getId()).build());
        assertEquals(AccountStatus.NORMAL.code(), after.getAccountStatus());
    }

    @Test
    @DisplayName("cancelAccount and borrow cannot both succeed concurrently")
    void testCancelAccountAndBorrowAreSerialized() throws Exception {
        String suffix = String.valueOf(System.nanoTime());
        User reader = createTestUserWithRole(
                "security_cancel_race_" + suffix,
                "cancelRace",
                "cancel-race-" + suffix + "@test.com",
                UserRole.READER.code()
        );
        Book book = createTestBook("注销借阅竞态图书-" + suffix, "竞态作者", 1);
        clearContext();

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger successCount = new AtomicInteger();
        Future<?> cancelFuture = executor.submit(() -> {
            start.await();
            setCurrentUser(reader.getId(), reader.getUserRole());
            try {
                if (Integer.valueOf(200).equals(userService.cancelAccount().getCode())) {
                    successCount.incrementAndGet();
                }
            } finally {
                clearContext();
            }
            return null;
        });
        Future<?> borrowFuture = executor.submit(() -> {
            start.await();
            setCurrentUser(reader.getId(), reader.getUserRole());
            try {
                if (Integer.valueOf(200).equals(borrowRecordService.borrow(book.getId()).getCode())) {
                    successCount.incrementAndGet();
                }
            } finally {
                clearContext();
            }
            return null;
        });

        start.countDown();
        cancelFuture.get(5, TimeUnit.SECONDS);
        borrowFuture.get(5, TimeUnit.SECONDS);
        executor.shutdown();
        assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));

        User storedUser = userMapper.getByActive(User.builder().id(reader.getId()).build());
        int activeBorrowCount = borrowRecordMapper.getActiveCountByUserId(reader.getId());
        assertEquals(1, successCount.get());
        if (AccountStatus.CANCELLED.code().equals(storedUser.getAccountStatus())) {
            assertEquals(0, activeBorrowCount);
        } else {
            assertEquals(AccountStatus.NORMAL.code(), storedUser.getAccountStatus());
            assertEquals(1, activeBorrowCount);
        }
    }

    @Test
    @DisplayName("batchDelete returns a business error for users with borrow history")
    void testBatchDeleteRejectsUserWithBorrowHistory() {
        String suffix = String.valueOf(System.nanoTime());
        User superAdmin = createTestUserWithRole(
                "security_delete_super_" + suffix,
                "deleteSuper",
                "delete-super-" + suffix + "@test.com",
                UserRole.SUPER_ADMIN.code()
        );
        User reader = createTestUserWithRole(
                "security_delete_reader_" + suffix,
                "deleteReader",
                "delete-reader-" + suffix + "@test.com",
                UserRole.READER.code()
        );
        Book book = createTestBook("删除历史测试图书-" + suffix, "历史作者", 1);
        BorrowRecord record = createTestBorrowRecord(
                reader.getId(), book.getId(), LocalDateTime.now().plusDays(7));
        borrowRecordMapper.updateReturnStatus(
                record.getId(), LocalDateTime.now(), BigDecimal.ZERO);
        bookMapper.increaseAvailableCount(book.getId());
        setCurrentUser(superAdmin.getId(), superAdmin.getUserRole());

        ApiResponse<String> result = userService.batchDelete(List.of(reader.getId()));

        assertEquals(400, result.getCode());
        assertNotNull(userMapper.getByActive(User.builder().id(reader.getId()).build()));
    }

    @Test
    @DisplayName("关键用户状态和角色变更均写入语义化审计日志")
    void testSensitiveUserChangesAreAudited() {
        User superAdmin = createTestUserWithRole(
                "audit_super_001", "审计超管", "audit_super_001@test.com",
                UserRole.SUPER_ADMIN.code());
        User reader = createTestUserWithRole(
                "audit_reader_001", "审计读者", "audit_reader_001@test.com",
                UserRole.READER.code());
        setCurrentUser(superAdmin.getId(), superAdmin.getUserRole());

        assertEquals(200, userService.freezeUser(reader.getId()).getCode());
        assertEquals(200, userService.unfreezeUser(reader.getId()).getCode());
        assertEquals(200, userService.backUpdate(UserAdminUpdateDto.builder()
                .id(reader.getId())
                .isWord(true)
                .userRole(UserRole.ACQUISITIONS.code())
                .build()).getCode());

        java.util.List<OperationLog> logs = operationLogMapper.selectList(null);
        assertTrue(logs.stream().anyMatch(log ->
                "用户状态".equals(log.getTarget())
                        && log.getDetail().contains("正常 -> 冻结")));
        assertTrue(logs.stream().anyMatch(log ->
                "用户状态".equals(log.getTarget())
                        && log.getDetail().contains("冻结 -> 正常")));
        assertTrue(logs.stream().anyMatch(log ->
                "用户发言状态".equals(log.getTarget())
                        && log.getDetail().contains("可发言 -> 禁言")));
        assertTrue(logs.stream().anyMatch(log ->
                "用户角色".equals(log.getTarget())
                        && log.getDetail().contains("读者 -> 采购员")));
    }

    private User createTestUserWithRole(String account, String userName, String email, Integer role) {
        User user = createTestUser(account, userName, email);
        userMapper.update(User.builder().id(user.getId()).userRole(role).build());
        user.setUserRole(role);
        return user;
    }

    private UserRegisterDto newUserRegisterDto(String account, String userName, String email) {
        UserRegisterDto dto = new UserRegisterDto();
        dto.setUserAccount(account);
        dto.setUserName(userName);
        dto.setUserEmail(email);
        dto.setUserPwd("Test@123456");
        return dto;
    }
}
