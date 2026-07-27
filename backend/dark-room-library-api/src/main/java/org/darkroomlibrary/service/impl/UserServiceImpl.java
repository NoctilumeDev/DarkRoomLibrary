package org.darkroomlibrary.service.impl;

import org.darkroomlibrary.context.CurrentUserContext;
import org.darkroomlibrary.mapper.BookMapper;
import org.darkroomlibrary.mapper.BookReservationMapper;
import org.darkroomlibrary.mapper.BorrowRecordMapper;
import org.darkroomlibrary.mapper.ProcurementOrderMapper;
import org.darkroomlibrary.mapper.UserMapper;
import org.darkroomlibrary.web.response.ApiResponse;
import org.darkroomlibrary.web.response.PageResponse;
import org.darkroomlibrary.web.dto.query.PageQuery;
import org.darkroomlibrary.web.dto.query.UserPageQuery;
import org.darkroomlibrary.web.dto.command.UserAdminUpdateDto;
import org.darkroomlibrary.web.dto.command.UserLoginDto;
import org.darkroomlibrary.web.dto.command.UserRegisterDto;
import org.darkroomlibrary.web.dto.command.UserUpdateDto;
import org.darkroomlibrary.web.dto.command.PasswordResetDto;
import org.darkroomlibrary.web.dto.command.PasswordUpdateDto;
import org.darkroomlibrary.domain.type.AccountStatus;
import org.darkroomlibrary.domain.type.FileReferenceType;
import org.darkroomlibrary.domain.type.LoginStatus;
import org.darkroomlibrary.domain.type.UserRole;
import org.darkroomlibrary.domain.type.VerificationCodePurpose;
import org.darkroomlibrary.domain.type.MuteStatus;
import org.darkroomlibrary.domain.model.User;
import org.darkroomlibrary.web.view.MetricPoint;
import org.darkroomlibrary.web.view.UserProfileView;
import org.darkroomlibrary.service.OperationAuditService;
import org.darkroomlibrary.service.CaptchaService;
import org.darkroomlibrary.service.FileStorageService;
import org.darkroomlibrary.service.LoginAttemptService;
import org.darkroomlibrary.service.ReservationWorkflowService;
import org.darkroomlibrary.service.UserService;
import org.darkroomlibrary.service.VerificationCodeService;
import org.darkroomlibrary.service.support.RecommendationSourceVersionService;
import org.darkroomlibrary.utils.AnalyticsTimeline;
import org.darkroomlibrary.utils.IdListUtils;
import org.darkroomlibrary.utils.JwtUtil;
import org.darkroomlibrary.utils.PasswordValidator;
import org.darkroomlibrary.utils.TransactionCallbacks;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.interceptor.TransactionAspectSupport;

import jakarta.annotation.Resource;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
@Slf4j
public class UserServiceImpl implements UserService {

    private static final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    @Resource
    private UserMapper userMapper;

    @Resource
    private UserEmailQuotaService userEmailQuotaService;

    @Resource
    private VerificationCodeService verificationCodeService;

    @Resource
    private LoginAttemptService loginAttemptService;

    @Resource
    private CaptchaService captchaService;

    @Resource
    private BorrowRecordMapper borrowRecordMapper;

    @Resource
    private BookReservationMapper bookReservationMapper;

    @Resource
    private BookMapper bookMapper;

    @Resource
    private ProcurementOrderMapper procurementOrderMapper;

    @Resource
    private OperationAuditService operationAuditService;

    @Resource
    private FileStorageService fileStorageService;

    @Resource
    private ReservationWorkflowService reservationWorkflowService;

    @Resource
    private RecommendationSourceVersionService recommendationSourceVersionService;

    @Resource
    private JwtUtil jwtUtil;

    @Override
    @Transactional
    public ApiResponse<String> register(UserRegisterDto userRegisterDTO) {
        if (userRegisterDTO.getVerificationCode() == null || userRegisterDTO.getVerificationCode().isEmpty()) {
            return ApiResponse.error("请输入邮箱验证码");
        }
        String invalidInput = validateRegistrationInput(userRegisterDTO);
        if (invalidInput != null) {
            return ApiResponse.error(invalidInput);
        }
        if (!verificationCodeService.verify(
                userRegisterDTO.getUserEmail(),
                VerificationCodePurpose.REGISTER.name(),
                userRegisterDTO.getVerificationCode())) {
            return ApiResponse.error("验证码错误或已过期");
        }
        return createReader(userRegisterDTO, "注册成功", true);
    }

    @Override
    public ApiResponse<Object> login(UserLoginDto userLoginDTO) {
        if (!captchaService.verify(userLoginDTO.getCaptchaId(), userLoginDTO.getCaptchaAnswer())) {
            return ApiResponse.error("验证码错误或已过期");
        }

        String account = userLoginDTO.getUserAccount();
        if (loginAttemptService.isBlocked(account)) {
            long seconds = loginAttemptService.getRemainingLockSeconds(account);
            long minutes = Math.max(1, seconds / 60);
            return ApiResponse.error("账户已被锁定，请" + minutes + "分钟后再试");
        }

        User user = userMapper.getByActive(User.builder().userAccount(account).build());
        if (user == null) {
            loginAttemptService.loginFailed(account);
            return ApiResponse.error("账号不存在");
        }
        if (Objects.equals(user.getAccountStatus(), AccountStatus.CANCELLED.code())) {
            return ApiResponse.error("账号已注销，无法登录");
        }
        if (Objects.equals(user.getAccountStatus(), AccountStatus.FROZEN.code())) {
            return ApiResponse.error("账户已被冻结，请联系管理员");
        }
        if (Boolean.TRUE.equals(user.getIsLogin())) {
            return ApiResponse.error("账户已被禁用，请联系管理员");
        }

        String storedPwd = user.getUserPwd();
        if (storedPwd == null || !storedPwd.startsWith("$2")) {
            return ApiResponse.error("账号密码格式异常，请使用忘记密码功能重置");
        }
        if (!encoder.matches(userLoginDTO.getUserPwd(), storedPwd)) {
            loginAttemptService.loginFailed(account);
            return ApiResponse.error("密码错误");
        }

        loginAttemptService.loginSucceeded(account);
        Map<String, Object> data = new HashMap<>();
        data.put("token", jwtUtil.toToken(user.getId(), user.getUserRole()));
        data.put("role", user.getUserRole());
        return ApiResponse.success("登录成功", data);
    }

    @Override
    public ApiResponse<UserProfileView> auth() {
        Integer userId = CurrentUserContext.userId();
        User user = userMapper.getByActive(User.builder().id(userId).build());
        if (user == null) {
            return ApiResponse.error("用户不存在");
        }
        return ApiResponse.success(toView(user));
    }

    @Override
    public ApiResponse<List<User>> query(UserPageQuery userPageQuery) {
        if (!isAdminOrSuper(CurrentUserContext.roleCode())) {
            return ApiResponse.error("无查询权限");
        }
        List<User> users = userMapper.query(userPageQuery);
        Integer count = userMapper.queryCount(userPageQuery);
        minimizeUserList(users);
        return PageResponse.success(users, count);
    }

    @Override
    @Transactional
    public ApiResponse<String> update(UserUpdateDto userUpdateDTO) {
        Integer userId = CurrentUserContext.userId();
        User existing = userMapper.findByIdForUpdate(userId);
        if (existing == null) {
            return ApiResponse.error("用户不存在");
        }
        String invalidProfile = validateOptionalProfileFields(
                userUpdateDTO.getUserName(), userUpdateDTO.getUserEmail());
        if (invalidProfile != null) {
            return ApiResponse.error(invalidProfile);
        }
        String conflict = findUserFieldConflict(
                userId, userUpdateDTO.getUserAccount(), userUpdateDTO.getUserName());
        if (conflict != null) {
            return ApiResponse.error(conflict);
        }
        String requestedEmail = userUpdateDTO.getUserEmail() == null
                ? null
                : userEmailQuotaService.normalize(userUpdateDTO.getUserEmail());
        boolean emailChanged = requestedEmail != null
                && !Objects.equals(
                        userEmailQuotaService.normalize(existing.getUserEmail()), requestedEmail);
        if (emailChanged) {
            if (userUpdateDTO.getVerificationCode() == null
                    || userUpdateDTO.getVerificationCode().isBlank()) {
                return ApiResponse.error("请输入新邮箱验证码");
            }
            if (!verificationCodeService.verify(
                    requestedEmail,
                    VerificationCodePurpose.CHANGE_EMAIL.name(),
                    userUpdateDTO.getVerificationCode())) {
                return ApiResponse.error("验证码错误或已过期");
            }
            if (!userEmailQuotaService.moveAccount(existing.getUserEmail(), requestedEmail)) {
                return ApiResponse.error(UserEmailQuotaService.LIMIT_MESSAGE);
            }
        }
        User updateEntity = User.builder()
                .id(userId)
                .userName(trimToNull(userUpdateDTO.getUserName()))
                .userAccount(userUpdateDTO.getUserAccount())
                .userAvatar(userUpdateDTO.getUserAvatar())
                .userEmail(requestedEmail)
                .build();
        try {
            if (userMapper.update(updateEntity) != 1) {
                TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
                return ApiResponse.error("用户状态已变化，请刷新后重试");
            }
        } catch (DuplicateKeyException e) {
            TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
            return ApiResponse.error(duplicateUserMessage(e));
        }
        if (userUpdateDTO.getUserAvatar() != null
                && !Objects.equals(userUpdateDTO.getUserAvatar(), existing.getUserAvatar())
                && !fileStorageService.bindSingle(
                userUpdateDTO.getUserAvatar(), FileReferenceType.USER_AVATAR, userId)) {
            TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
            return ApiResponse.error("头像文件无效或不属于当前用户");
        }
        return ApiResponse.success("保存成功");
    }

    @Override
    @Transactional
    public ApiResponse<String> batchDelete(List<Integer> ids) {
        if (ids == null || ids.isEmpty()) {
            return ApiResponse.error("请选择要删除的用户");
        }
        List<Integer> normalizedIds = ids.stream()
                .filter(Objects::nonNull)
                .distinct()
                .sorted()
                .collect(Collectors.toList());
        if (normalizedIds.isEmpty()) {
            return ApiResponse.error("请选择要删除的用户");
        }
        if (IdListUtils.exceedsBatchLimit(normalizedIds)) {
            return ApiResponse.error("单次最多删除" + IdListUtils.MAX_BATCH_SIZE + "个用户");
        }
        Integer currentUserId = CurrentUserContext.userId();
        Integer currentRoleId = CurrentUserContext.roleCode();
        boolean currentIsSuperAdmin = Objects.equals(currentRoleId, UserRole.SUPER_ADMIN.code());
        List<User> targets = new ArrayList<>(normalizedIds.size());

        for (Integer id : normalizedIds) {
            if (Objects.equals(id, currentUserId)) {
                return ApiResponse.error("不能删除自己");
            }
            User target = userMapper.findByIdForUpdate(id);
            if (target == null) {
                return ApiResponse.error("用户不存在");
            }
            if (!currentIsSuperAdmin
                    && !Objects.equals(target.getUserRole(), UserRole.READER.code())) {
                return ApiResponse.error("普通管理员只能删除读者账号");
            }
            targets.add(target);
        }
        if (borrowRecordMapper.countByUserIds(normalizedIds) > 0) {
            return ApiResponse.error("存在借阅历史，不能删除用户；可改为冻结账号");
        }
        if (bookReservationMapper.countActiveByUserIds(normalizedIds) > 0) {
            return ApiResponse.error("存在进行中的预约，不能删除用户；请先处理预约");
        }
        if (procurementOrderMapper.countActiveByUserIds(normalizedIds) > 0) {
            return ApiResponse.error("用户仍参与进行中的采购单，不能删除；可改为冻结账号");
        }

        userEmailQuotaService.releaseAccounts(
                targets.stream().map(User::getUserEmail).collect(Collectors.toList()));
        fileStorageService.releaseUserBusinessFiles(normalizedIds);
        if (userMapper.batchDelete(normalizedIds) != normalizedIds.size()) {
            TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
            return ApiResponse.error("部分用户状态已变化，请刷新后重试");
        }
        recommendationSourceVersionService.invalidateGlobalAfterCommit();
        return ApiResponse.success("删除成功");
    }

    @Override
    @Transactional
    public ApiResponse<String> updatePwd(PasswordUpdateDto dto) {
        String oldPwd = dto.getOldPwd();
        String newPwd = dto.getNewPwd();
        String againPwd = dto.getAgainPwd();
        if (oldPwd == null || oldPwd.isEmpty()) {
            return ApiResponse.error("原始密码不能为空");
        }
        if (newPwd == null || newPwd.isEmpty()) {
            return ApiResponse.error("请输入新密码");
        }
        if (againPwd == null || againPwd.isEmpty()) {
            return ApiResponse.error("请确认新密码");
        }
        if (!newPwd.equals(againPwd)) {
            return ApiResponse.error("两次密码输入不一致");
        }
        if (!PasswordValidator.isValid(newPwd)) {
            return ApiResponse.error(PasswordValidator.getRequirement());
        }

        User user = userMapper.findByIdForUpdate(CurrentUserContext.userId());
        if (user == null) {
            return ApiResponse.error("用户不存在");
        }
        String storedPwd = user.getUserPwd();
        if (storedPwd == null || !storedPwd.startsWith("$2")) {
            return ApiResponse.error("账号密码格式异常，请使用忘记密码功能重置");
        }
        if (!encoder.matches(oldPwd, storedPwd)) {
            return ApiResponse.error("原始密码验证失败");
        }

        if (userMapper.update(User.builder()
                .id(user.getId())
                .userPwd(encoder.encode(newPwd))
                .build()) != 1) {
            return ApiResponse.error("用户状态已变化，请刷新后重试");
        }
        return ApiResponse.success("密码修改成功");
    }

    @Override
    public ApiResponse<UserProfileView> getById(Integer id) {
        Integer currentUserId = CurrentUserContext.userId();
        if (!CurrentUserContext.isAdministrator() && !Objects.equals(currentUserId, id)) {
            return ApiResponse.error("无操作权限");
        }
        User user = userMapper.getByActive(User.builder().id(id).build());
        if (user == null) {
            return ApiResponse.error("用户不存在");
        }
        return ApiResponse.success(toView(user));
    }

    @Override
    @Transactional
    public ApiResponse<String> insert(UserRegisterDto userRegisterDTO) {
        Integer currentRoleId = CurrentUserContext.roleCode();
        if (!isAdminOrSuper(currentRoleId)) {
            return ApiResponse.error("无操作权限");
        }
        Integer requestedRole = userRegisterDTO.getUserRole() != null
                ? userRegisterDTO.getUserRole()
                : UserRole.READER.code();
        if (!isKnownRole(requestedRole)) {
            return ApiResponse.error("角色无效");
        }
        if (!isSuperAdminRole(currentRoleId) && !isReaderRole(requestedRole)) {
            return ApiResponse.error("普通管理员只能新增读者账号");
        }
        boolean coordinatorAdmin = Boolean.TRUE.equals(userRegisterDTO.getIsCoordinatorAdmin());
        if (coordinatorAdmin && !isSuperAdminRole(currentRoleId)) {
            return ApiResponse.error("只有超级管理员可以任命馆务协调员");
        }
        if (coordinatorAdmin && !isAdminRole(requestedRole)) {
            return ApiResponse.error("只有管理员可以被任命为馆务协调员");
        }
        return createUser(userRegisterDTO, "新增成功", requestedRole, coordinatorAdmin);
    }

    @Override
    @Transactional
    public ApiResponse<String> backUpdate(UserAdminUpdateDto dto) {
        Integer currentUserId = CurrentUserContext.userId();
        Integer currentRoleId = CurrentUserContext.roleCode();
        if (!isAdminOrSuper(currentRoleId)) {
            return ApiResponse.error("无操作权限");
        }
        User target = userMapper.findByIdForUpdate(dto.getId());
        if (target == null) {
            return ApiResponse.error("用户不存在");
        }

        boolean currentIsSuperAdmin = Objects.equals(currentRoleId, UserRole.SUPER_ADMIN.code());
        boolean currentIsAdmin = Objects.equals(currentRoleId, UserRole.ADMIN.code());
        boolean targetIsSuperAdmin = Objects.equals(target.getUserRole(), UserRole.SUPER_ADMIN.code());
        boolean targetIsAdmin = Objects.equals(target.getUserRole(), UserRole.ADMIN.code());
        boolean targetCancelled = Objects.equals(target.getAccountStatus(), AccountStatus.CANCELLED.code());
        Integer requestedRole = dto.getUserRole();

        if (targetCancelled) {
            return ApiResponse.error("已注销账号不能修改");
        }
        if (targetIsSuperAdmin && !currentIsSuperAdmin) {
            return ApiResponse.error("普通管理员不能修改超级管理员");
        }
        if (currentIsAdmin && !Objects.equals(dto.getId(), currentUserId) && !isReaderRole(target.getUserRole())) {
            return ApiResponse.error("普通管理员只能修改读者账号");
        }
        if (currentIsAdmin && targetIsAdmin && !Objects.equals(dto.getId(), currentUserId)) {
            return ApiResponse.error("不能修改同级管理员");
        }
        if (requestedRole != null && !isKnownRole(requestedRole)) {
            return ApiResponse.error("角色无效");
        }
        Integer roleAfterUpdate = requestedRole != null ? requestedRole : target.getUserRole();
        ApiResponse<Boolean> coordinatorAdminResult = resolveCoordinatorAdminUpdate(dto, target, currentIsSuperAdmin, roleAfterUpdate);
        if (coordinatorAdminResult.getCode() != 200) {
            return ApiResponse.error(coordinatorAdminResult.getMsg());
        }
        Boolean coordinatorAdminUpdate = coordinatorAdminResult.getData();
        boolean loginStatusChanged = dto.getIsLogin() != null
                && !Objects.equals(dto.getIsLogin(), target.getIsLogin());
        boolean wordStatusChanged = dto.getIsWord() != null
                && !Objects.equals(dto.getIsWord(), target.getIsWord());
        boolean roleChanged = dto.getUserRole() != null
                && !Objects.equals(dto.getUserRole(), target.getUserRole());
        boolean coordinatorAdminChanged = coordinatorAdminUpdate != null
                && !Objects.equals(coordinatorAdminUpdate, target.getIsCoordinatorAdmin());
        boolean passwordResetRequested = dto.getUserPwd() != null && !dto.getUserPwd().isBlank();
        if (passwordResetRequested && !currentIsSuperAdmin) {
            return ApiResponse.error("只有超级管理员可以重置其他用户密码");
        }
        if (passwordResetRequested && Objects.equals(dto.getId(), currentUserId)) {
            return ApiResponse.error("修改自己的密码必须验证原密码");
        }
        if (passwordResetRequested && !PasswordValidator.isValid(dto.getUserPwd())) {
            return ApiResponse.error(PasswordValidator.getRequirement());
        }
        if (!currentIsSuperAdmin
                && requestedRole != null
                && !Objects.equals(requestedRole, target.getUserRole())) {
            return ApiResponse.error("普通管理员不能变更用户角色");
        }
        if (Objects.equals(dto.getId(), currentUserId)
                && requestedRole != null
                && !Objects.equals(requestedRole, target.getUserRole())) {
            return ApiResponse.error("不能修改自己的角色");
        }
        if (Boolean.TRUE.equals(dto.getIsLogin()) && Objects.equals(dto.getId(), currentUserId)) {
            return ApiResponse.error("不能禁用自己");
        }
        if (Boolean.FALSE.equals(dto.getIsLogin()) && Objects.equals(dto.getId(), currentUserId)) {
            return ApiResponse.error("不能解禁自己");
        }
        if (Boolean.TRUE.equals(dto.getIsLogin()) && targetIsSuperAdmin) {
            return ApiResponse.error("不能禁用超级管理员");
        }
        String roleTransitionError = validateRoleTransition(target, roleAfterUpdate, roleChanged);
        if (roleTransitionError != null) {
            return ApiResponse.error(roleTransitionError);
        }
        String invalidProfile = validateOptionalProfileFields(dto.getUserName(), dto.getUserEmail());
        if (invalidProfile != null) {
            return ApiResponse.error(invalidProfile);
        }
        String conflict = findUserFieldConflict(dto.getId(), dto.getUserAccount(), dto.getUserName());
        if (conflict != null) {
            return ApiResponse.error(conflict);
        }
        String requestedEmail = dto.getUserEmail() == null
                ? null
                : userEmailQuotaService.normalize(dto.getUserEmail());
        if (requestedEmail != null
                && !userEmailQuotaService.moveAccount(target.getUserEmail(), requestedEmail)) {
            return ApiResponse.error(UserEmailQuotaService.LIMIT_MESSAGE);
        }

        User updateEntity = User.builder()
                .id(dto.getId())
                .userName(trimToNull(dto.getUserName()))
                .userAccount(dto.getUserAccount())
                .userPwd(passwordResetRequested ? encoder.encode(dto.getUserPwd()) : null)
                .userAvatar(dto.getUserAvatar())
                .userEmail(requestedEmail)
                .isLogin(dto.getIsLogin())
                .accountStatus(resolveAccountStatusByLoginFlag(dto.getIsLogin()))
                .isWord(dto.getIsWord())
                .userRole(dto.getUserRole())
                .isCoordinatorAdmin(coordinatorAdminUpdate)
                .build();
        try {
            if (userMapper.update(updateEntity) != 1) {
                TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
                return ApiResponse.error("用户状态已变化，请刷新后重试");
            }
        } catch (DuplicateKeyException e) {
            TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
            return ApiResponse.error(duplicateUserMessage(e));
        }
        if (dto.getUserAvatar() != null
                && !Objects.equals(dto.getUserAvatar(), target.getUserAvatar())
                && !fileStorageService.bindSingle(
                dto.getUserAvatar(), FileReferenceType.USER_AVATAR, dto.getId())) {
            TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
            return ApiResponse.error("头像文件无效或不属于当前用户");
        }
        if (isReaderRole(target.getUserRole())
                && (Boolean.TRUE.equals(dto.getIsLogin()) || !isReaderRole(roleAfterUpdate))) {
            releaseReaderReservations(target.getId());
        }
        if (passwordResetRequested) {
            clearLoginAttemptsAfterCommit(target.getUserAccount(), dto.getUserAccount());
            operationAuditService.record("重置", "用户密码",
                    userIdentity(target) + "，密码已由超级管理员重置");
        }
        recordUserUpdateAudits(
                target, dto, loginStatusChanged, wordStatusChanged, roleChanged,
                coordinatorAdminChanged, coordinatorAdminUpdate);
        if (loginStatusChanged || roleChanged) {
            recommendationSourceVersionService.invalidateGlobalAfterCommit();
        }
        return ApiResponse.success(passwordResetRequested ? "保存成功，密码已重置" : "保存成功");
    }

    @Override
    public ApiResponse<List<MetricPoint>> queryByDays(Integer day) {
        PageQuery queryDto = AnalyticsTimeline.queryWindow(day);
        return ApiResponse.success(AnalyticsTimeline.toDailyMetrics(
                day,
                userMapper.dailyCreateStats(queryDto.getStartTime(), queryDto.getEndTime())
        ));
    }

    @Override
    @Transactional
    public ApiResponse<String> resetPwd(PasswordResetDto dto) {
        String account = dto.getAccount();
        String email = dto.getEmail();
        String code = dto.getCode();
        String newPwd = dto.getNewPwd();
        if (account == null || account.isEmpty()) {
            return ApiResponse.error("请输入账号");
        }
        if (email == null || email.isEmpty()) {
            return ApiResponse.error("请输入邮箱");
        }
        if (code == null || code.isEmpty()) {
            return ApiResponse.error("请输入验证码");
        }
        if (newPwd == null || newPwd.isEmpty()) {
            return ApiResponse.error("请输入新密码");
        }
        if (!PasswordValidator.isValid(newPwd)) {
            return ApiResponse.error(PasswordValidator.getRequirement());
        }

        User snapshot = userMapper.getByActive(User.builder().userAccount(account).build());
        if (snapshot == null) {
            return ApiResponse.error("账号不存在");
        }
        User user = userMapper.findByIdForUpdate(snapshot.getId());
        if (user == null || !Objects.equals(user.getUserAccount(), account)) {
            return ApiResponse.error("账号状态已变化，请重新提交");
        }
        if (user.getUserEmail() == null || !user.getUserEmail().equalsIgnoreCase(email)) {
            return ApiResponse.error("邮箱与账号不匹配");
        }
        if (!verificationCodeService.verify(email, VerificationCodePurpose.RESET_PASSWORD.name(), code)) {
            return ApiResponse.error("验证码错误或已过期");
        }

        if (userMapper.update(User.builder()
                .id(user.getId())
                .userPwd(encoder.encode(newPwd))
                .build()) != 1) {
            return ApiResponse.error("用户状态已变化，请重新提交");
        }
        clearLoginAttemptsAfterCommit(user.getUserAccount());
        return ApiResponse.success("密码重置成功，请使用新密码登录");
    }

    @Override
    public ApiResponse<String> sendVerifyCode(String email) {
        return sendVerifyCode(email, VerificationCodePurpose.REGISTER.name());
    }

    @Override
    public ApiResponse<String> sendVerifyCode(String email, String purpose) {
        if (email == null || email.isEmpty()) {
            return ApiResponse.error("请输入邮箱");
        }
        String codePurpose = (purpose == null || purpose.trim().isEmpty())
                ? VerificationCodePurpose.REGISTER.name()
                : purpose;
        if (VerificationCodePurpose.from(codePurpose)
                .filter(value -> value == VerificationCodePurpose.CHANGE_EMAIL)
                .isPresent()
                && CurrentUserContext.userId() == null) {
            return ApiResponse.error("身份认证异常，请先登录");
        }
        return verificationCodeService.sendCode(email, codePurpose);
    }

    @Override
    @Transactional
    public ApiResponse<String> freezeUser(Integer userId) {
        ApiResponse<User> checkResult = checkStatusChangePermission(userId, "冻结", true);
        if (checkResult.getCode() != 200) {
            return ApiResponse.error(checkResult.getMsg());
        }
        User target = checkResult.getData();
        boolean changed = !Objects.equals(target.getAccountStatus(), AccountStatus.FROZEN.code())
                || !Boolean.TRUE.equals(target.getIsLogin());
        if (userMapper.update(User.builder()
                .id(userId)
                .isLogin(true)
                .accountStatus(AccountStatus.FROZEN.code())
                .build()) != 1) {
            return ApiResponse.error("用户状态已变化，请刷新后重试");
        }
        if (isReaderRole(target.getUserRole())) {
            releaseReaderReservations(target.getId());
        }
        if (changed) {
            operationAuditService.record("修改", "用户状态",
                    userIdentity(target) + "，账号状态："
                            + accountStatusName(target.getAccountStatus()) + " -> 冻结");
        }
        recommendationSourceVersionService.invalidateGlobalAfterCommit();
        return ApiResponse.success("用户已冻结");
    }

    @Override
    @Transactional
    public ApiResponse<String> unfreezeUser(Integer userId) {
        ApiResponse<User> checkResult = checkStatusChangePermission(userId, "解冻", false);
        if (checkResult.getCode() != 200) {
            return ApiResponse.error(checkResult.getMsg());
        }
        User target = checkResult.getData();
        if (Objects.equals(target.getAccountStatus(), AccountStatus.CANCELLED.code())) {
            return ApiResponse.error("已注销账号不能解冻");
        }
        if (userMapper.update(User.builder()
                .id(userId)
                .isLogin(false)
                .accountStatus(AccountStatus.NORMAL.code())
                .build()) != 1) {
            return ApiResponse.error("用户状态已变化，请刷新后重试");
        }
        if (!Objects.equals(target.getAccountStatus(), AccountStatus.NORMAL.code())
                || !Boolean.FALSE.equals(target.getIsLogin())) {
            operationAuditService.record("修改", "用户状态",
                    userIdentity(target) + "，账号状态："
                            + accountStatusName(target.getAccountStatus()) + " -> 正常");
        }
        recommendationSourceVersionService.invalidateGlobalAfterCommit();
        return ApiResponse.success("用户已解冻");
    }

    @Override
    @Transactional
    public ApiResponse<String> cancelAccount() {
        Integer userId = CurrentUserContext.userId();
        User user = userMapper.findByIdForUpdate(userId);
        if (user == null) {
            return ApiResponse.error("用户不存在");
        }
        if (!isReaderRole(user.getUserRole())) {
            return ApiResponse.error("只有读者账号可以自助注销");
        }
        if (Objects.equals(user.getAccountStatus(), AccountStatus.CANCELLED.code())) {
            return ApiResponse.error("账号已注销");
        }
        if (Objects.equals(user.getAccountStatus(), AccountStatus.FROZEN.code())
                || Boolean.TRUE.equals(user.getIsLogin())) {
            return ApiResponse.error("账号处于冻结状态，请联系管理员处理");
        }
        if (borrowRecordMapper.getActiveCountByUserId(userId) > 0) {
            return ApiResponse.error("存在未归还图书，不能注销账号");
        }
        BigDecimal fineAmount = borrowRecordMapper.sumFineAmountByUserId(userId);
        if (fineAmount != null && fineAmount.compareTo(BigDecimal.ZERO) > 0) {
            return ApiResponse.error("存在未处理罚款，不能注销账号");
        }
        if (bookReservationMapper.countActiveByUserId(userId) > 0) {
            return ApiResponse.error("存在进行中的预约，不能注销账号");
        }

        if (userMapper.update(User.builder()
                .id(userId)
                .isLogin(true)
                .accountStatus(AccountStatus.CANCELLED.code())
                .build()) != 1) {
            return ApiResponse.error("用户状态已变化，请刷新后重试");
        }
        recommendationSourceVersionService.invalidateGlobalAfterCommit();
        return ApiResponse.success("账号已注销");
    }

    @Override
    public ApiResponse<List<UserProfileView>> queryCollaborationUsers(Integer role) {
        Integer currentRole = CurrentUserContext.roleCode();
        boolean targetPurchaser = Objects.equals(role, UserRole.ACQUISITIONS.code());
        boolean targetLogistics = Objects.equals(role, UserRole.LOGISTICS.code());
        if (!targetPurchaser && !targetLogistics) {
            return ApiResponse.error("协作角色不正确");
        }
        if (Objects.equals(currentRole, UserRole.ACQUISITIONS.code()) && !targetLogistics) {
            return ApiResponse.error("采购员只能查询物流人员");
        }
        if (!isAdminOrSuper(currentRole)
                && !Objects.equals(currentRole, UserRole.ACQUISITIONS.code())) {
            return ApiResponse.error("无查询权限");
        }
        UserPageQuery query = new UserPageQuery();
        query.setRole(role);
        query.setAccountStatus(AccountStatus.NORMAL.code());
        query.setIsLogin(false);
        List<UserProfileView> users = userMapper.query(query).stream().map(user -> {
            UserProfileView vo = new UserProfileView();
            vo.setId(user.getId());
            vo.setUserName(user.getUserName());
            vo.setUserRole(user.getUserRole());
            return vo;
        }).collect(Collectors.toList());
        return ApiResponse.success(users);
    }

    private ApiResponse<String> createReader(UserRegisterDto dto, String successMessage, boolean requireEmailCode) {
        if (requireEmailCode && (dto.getVerificationCode() == null || dto.getVerificationCode().isEmpty())) {
            return ApiResponse.error("请输入邮箱验证码");
        }
        return createUser(dto, successMessage, UserRole.READER.code(), false);
    }

    private ApiResponse<String> createUser(UserRegisterDto dto, String successMessage, Integer role, boolean coordinatorAdmin) {
        String conflict = validateNewUser(dto);
        if (conflict != null) {
            return ApiResponse.error(conflict);
        }
        String normalizedEmail = userEmailQuotaService.normalize(dto.getUserEmail());

        User saveEntity = User.builder()
                .userRole(role)
                .isCoordinatorAdmin(coordinatorAdmin && isAdminRole(role))
                .userName(dto.getUserName().trim())
                .userAccount(dto.getUserAccount())
                .userAvatar(dto.getUserAvatar())
                .userPwd(encoder.encode(dto.getUserPwd()))
                .userEmail(normalizedEmail)
                .createTime(LocalDateTime.now())
                .accountStatus(AccountStatus.NORMAL.code())
                .isLogin(LoginStatus.ACTIVE.disabled())
                .isWord(MuteStatus.ACTIVE.muted())
                .build();
        if (!userEmailQuotaService.reserveNewAccount(normalizedEmail)) {
            return ApiResponse.error(UserEmailQuotaService.LIMIT_MESSAGE);
        }
        try {
            if (userMapper.insert(saveEntity) != 1) {
                TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
                return ApiResponse.error("用户创建失败，请重试");
            }
        } catch (DuplicateKeyException e) {
            TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
            return ApiResponse.error(duplicateUserMessage(e));
        }
        if (!fileStorageService.bindSingle(
                dto.getUserAvatar(), FileReferenceType.USER_AVATAR, saveEntity.getId())) {
            TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
            return ApiResponse.error("头像文件无效或不属于当前用户");
        }
        return ApiResponse.success(successMessage);
    }

    private String validateNewUser(UserRegisterDto dto) {
        if (!PasswordValidator.isValid(dto.getUserPwd())) {
            return PasswordValidator.getRequirement();
        }
        String normalizedName = dto.getUserName().trim();
        if (userMapper.getByActive(User.builder().userName(normalizedName).build()) != null) {
            return "用户名已经被使用，请换一个";
        }
        if (userMapper.getByActive(User.builder().userAccount(dto.getUserAccount()).build()) != null) {
            return "账号不可用";
        }
        if (userEmailQuotaService.normalize(dto.getUserEmail()) == null) {
            return "邮箱不能为空";
        }
        return null;
    }

    private String validateRegistrationInput(UserRegisterDto dto) {
        if (!PasswordValidator.isValid(dto.getUserPwd())) {
            return PasswordValidator.getRequirement();
        }
        if (userEmailQuotaService.normalize(dto.getUserEmail()) == null) {
            return "邮箱不能为空";
        }
        return null;
    }

    private ApiResponse<Boolean> resolveCoordinatorAdminUpdate(UserAdminUpdateDto dto,
                                                   User target,
                                                   boolean currentIsSuperAdmin,
                                                   Integer roleAfterUpdate) {
        Boolean requestedCoordinatorAdmin = dto.getIsCoordinatorAdmin();
        boolean currentCoordinatorAdmin = Boolean.TRUE.equals(target.getIsCoordinatorAdmin());
        if (requestedCoordinatorAdmin != null) {
            boolean targetCoordinatorAdmin = Boolean.TRUE.equals(requestedCoordinatorAdmin);
            if (!currentIsSuperAdmin && targetCoordinatorAdmin != currentCoordinatorAdmin) {
                return ApiResponse.error("只有超级管理员可以任免馆务协调员");
            }
            if (targetCoordinatorAdmin && !isAdminRole(roleAfterUpdate)) {
                return ApiResponse.error("只有管理员可以被任命为馆务协调员");
            }
            return ApiResponse.success(targetCoordinatorAdmin);
        }
        if (!isAdminRole(roleAfterUpdate) && currentCoordinatorAdmin) {
            return ApiResponse.success(false);
        }
        return ApiResponse.success((Boolean) null);
    }

    private Integer resolveAccountStatusByLoginFlag(Boolean isLogin) {
        if (isLogin == null) {
            return null;
        }
        return Boolean.TRUE.equals(isLogin)
                ? AccountStatus.FROZEN.code()
                : AccountStatus.NORMAL.code();
    }

    private String validateRoleTransition(User target, Integer roleAfterUpdate, boolean roleChanged) {
        if (!roleChanged) {
            return null;
        }
        if (isReaderRole(target.getUserRole()) && !isReaderRole(roleAfterUpdate)) {
            if (borrowRecordMapper.getActiveCountByUserId(target.getId()) > 0) {
                return "用户仍有未归还图书，不能变更为非读者角色";
            }
            BigDecimal fineAmount = borrowRecordMapper.sumFineAmountByUserId(target.getId());
            if (fineAmount != null && fineAmount.compareTo(BigDecimal.ZERO) > 0) {
                return "用户仍有未处理罚款，不能变更为非读者角色";
            }
        }
        if (procurementOrderMapper.countActiveByUserIds(List.of(target.getId())) > 0) {
            return "用户仍参与进行中的采购单，不能变更角色";
        }
        return null;
    }

    private void releaseReaderReservations(Integer userId) {
        List<Integer> bookIds = bookReservationMapper.findActiveBookIdsByUserId(userId);
        if (bookIds.isEmpty()) {
            return;
        }
        bookMapper.findByIdsForUpdate(bookIds);
        if (bookReservationMapper.releaseActiveByUserId(userId) == 0) {
            return;
        }
        TransactionCallbacks.afterCommit(() -> bookIds.forEach(bookId -> {
            try {
                reservationWorkflowService.onBookReturned(bookId);
            } catch (Exception e) {
                log.warn("用户状态变化后的预约递补失败，等待定时对账: userId={}, bookId={}, error={}",
                        userId, bookId, e.getMessage());
            }
        }));
    }

    private void recordUserUpdateAudits(User target,
                                        UserAdminUpdateDto dto,
                                        boolean loginStatusChanged,
                                        boolean wordStatusChanged,
                                        boolean roleChanged,
                                        boolean coordinatorAdminChanged,
                                        Boolean coordinatorAdminUpdate) {
        String identity = userIdentity(target);
        if (loginStatusChanged) {
            String before = Boolean.TRUE.equals(target.getIsLogin()) ? "冻结" : "正常";
            String after = Boolean.TRUE.equals(dto.getIsLogin()) ? "冻结" : "正常";
            operationAuditService.record("修改", "用户状态",
                    identity + "，账号状态：" + before + " -> " + after);
        }
        if (wordStatusChanged) {
            String before = Boolean.TRUE.equals(target.getIsWord()) ? "禁言" : "可发言";
            String after = Boolean.TRUE.equals(dto.getIsWord()) ? "禁言" : "可发言";
            operationAuditService.record("修改", "用户发言状态",
                    identity + "，发言状态：" + before + " -> " + after);
        }
        if (roleChanged) {
            operationAuditService.record("修改", "用户角色",
                    identity + "，角色：" + roleName(target.getUserRole())
                            + " -> " + roleName(dto.getUserRole()));
        }
        if (coordinatorAdminChanged) {
            String before = Boolean.TRUE.equals(target.getIsCoordinatorAdmin()) ? "馆务协调员" : "普通管理员";
            String after = Boolean.TRUE.equals(coordinatorAdminUpdate) ? "馆务协调员" : "普通管理员";
            operationAuditService.record("修改", "用户角色",
                    identity + "，管理级别：" + before + " -> " + after);
        }
    }

    private String userIdentity(User user) {
        return "用户ID=" + user.getId()
                + "，账号=" + user.getUserAccount()
                + "，姓名=" + user.getUserName();
    }

    private String accountStatusName(Integer status) {
        if (Objects.equals(status, AccountStatus.FROZEN.code())) {
            return "冻结";
        }
        if (Objects.equals(status, AccountStatus.CANCELLED.code())) {
            return "注销";
        }
        return "正常";
    }

    private String roleName(Integer roleId) {
        String roleName = UserRole.displayNameOf(roleId);
        return roleName == null ? "未知角色(" + roleId + ")" : roleName;
    }

    private ApiResponse<User> checkStatusChangePermission(Integer targetUserId, String actionName, boolean blockSuperTarget) {
        Integer currentUserId = CurrentUserContext.userId();
        Integer currentRoleId = CurrentUserContext.roleCode();
        if (!isAdminOrSuper(currentRoleId)) {
            return ApiResponse.error("无操作权限");
        }
        if (Objects.equals(targetUserId, currentUserId)) {
            return ApiResponse.error("不能" + actionName + "自己");
        }
        User target = userMapper.findByIdForUpdate(targetUserId);
        if (target == null) {
            return ApiResponse.error("用户不存在");
        }
        if (blockSuperTarget && isSuperAdminRole(target.getUserRole())) {
            return ApiResponse.error("不能冻结超级管理员");
        }
        if (Objects.equals(target.getAccountStatus(), AccountStatus.CANCELLED.code())) {
            return ApiResponse.error("已注销账号不能" + actionName);
        }
        if (!isSuperAdminRole(currentRoleId) && !isReaderRole(target.getUserRole())) {
            return ApiResponse.error("普通管理员只能" + actionName + "读者账号");
        }
        return ApiResponse.success(target);
    }

    private void minimizeUserList(List<User> users) {
        if (isSuperAdminRole(CurrentUserContext.roleCode()) || users == null) {
            return;
        }
        for (User user : users) {
            if (!isReaderRole(user.getUserRole())) {
                user.setUserEmail(maskEmail(user.getUserEmail()));
            }
        }
    }

    private boolean isAdminOrSuper(Integer roleId) {
        return Objects.equals(roleId, UserRole.ADMIN.code()) || isSuperAdminRole(roleId);
    }

    private boolean isAdminRole(Integer roleId) {
        return Objects.equals(roleId, UserRole.ADMIN.code());
    }

    private boolean isSuperAdminRole(Integer roleId) {
        return Objects.equals(roleId, UserRole.SUPER_ADMIN.code());
    }

    private boolean isReaderRole(Integer roleId) {
        return Objects.equals(roleId, UserRole.READER.code());
    }

    private boolean isKnownRole(Integer roleId) {
        return UserRole.displayNameOf(roleId) != null;
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

    private String findUserFieldConflict(Integer currentId, String account, String userName) {
        if (account != null && !account.isBlank()) {
            User accountOwner = userMapper.getByActive(User.builder().userAccount(account).build());
            if (accountOwner != null && !Objects.equals(accountOwner.getId(), currentId)) {
                return "账号不可用";
            }
        }
        if (userName != null && !userName.isBlank()) {
            User nameOwner = userMapper.getByActive(User.builder().userName(userName.trim()).build());
            if (nameOwner != null && !Objects.equals(nameOwner.getId(), currentId)) {
                return "用户名已经被使用，请换一个";
            }
        }
        return null;
    }

    private String validateOptionalProfileFields(String userName, String email) {
        if (userName != null && userName.trim().isEmpty()) {
            return "用户名不能为空";
        }
        if (email != null && email.trim().isEmpty()) {
            return "邮箱不能为空";
        }
        return null;
    }

    private String duplicateUserMessage(DuplicateKeyException e) {
        String message = e.getMostSpecificCause() == null
                ? e.getMessage()
                : e.getMostSpecificCause().getMessage();
        String normalized = message == null ? "" : message.toLowerCase();
        if (normalized.contains("uk_user_name") || normalized.contains("user_name")) {
            return "用户名已经被使用，请换一个";
        }
        return "账号不可用";
    }

    private void clearLoginAttemptsAfterCommit(String... accounts) {
        List<String> normalizedAccounts = java.util.Arrays.stream(accounts)
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(account -> !account.isEmpty())
                .distinct()
                .collect(Collectors.toList());
        if (normalizedAccounts.isEmpty()) {
            return;
        }
        TransactionCallbacks.afterCommit(() ->
                normalizedAccounts.forEach(loginAttemptService::loginSucceeded));
    }

    private String trimToNull(String value) {
        return value == null ? null : value.trim();
    }

    private UserProfileView toView(User user) {
        UserProfileView userView = new UserProfileView();
        BeanUtils.copyProperties(user, userView);
        return userView;
    }
}
