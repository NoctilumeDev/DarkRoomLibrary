package org.darkroomlibrary.service.impl;

import org.darkroomlibrary.context.CurrentUserContext;
import org.darkroomlibrary.infrastructure.security.UserAuthLookup;
import org.darkroomlibrary.domain.model.OperationLog;
import org.darkroomlibrary.service.OperationAuditService;
import org.darkroomlibrary.service.OperationLogService;
import org.darkroomlibrary.utils.TransactionCallbacks;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;

@Slf4j
@Service
public class OperationAuditServiceImpl implements OperationAuditService {

    @Resource
    private OperationLogService operationLogService;

    @Resource
    private UserAuthLookup userAuthLookup;

    @Override
    public void record(String operation, String target, String detail) {
        try {
            OperationLog operationLog = buildOperationLog(operation, target, detail);
            if (operationLog == null) {
                return;
            }
            TransactionCallbacks.afterCommit(() -> persistSafely(operationLog));
        } catch (Exception e) {
            log.error("准备操作日志失败: operation={}, target={}", operation, target, e);
        }
    }

    private OperationLog buildOperationLog(String operation, String target, String detail) {
        Integer userId = CurrentUserContext.userId();
        if (userId == null) {
            return null;
        }
        UserAuthLookup.AuthUser user = userAuthLookup.getActiveUser(userId).orElse(null);
        String userName = user == null ? "用户#" + userId : user.getUserName();
        return OperationLog.builder()
                .userId(userId)
                .userName(userName)
                .operation(operation)
                .target(target)
                .detail(detail)
                .ip(resolveClientIp())
                .build();
    }

    private void persistSafely(OperationLog operationLog) {
        try {
            operationLogService.record(operationLog);
        } catch (Exception e) {
            log.error("操作日志落库失败: operation={}, target={}",
                    operationLog.getOperation(), operationLog.getTarget(), e);
        }
    }

    private String resolveClientIp() {
        ServletRequestAttributes attributes =
                (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes == null) {
            return null;
        }
        HttpServletRequest request = attributes.getRequest();
        return request.getRemoteAddr();
    }
}
