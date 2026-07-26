package org.darkroomlibrary.service.impl;

import org.darkroomlibrary.context.CurrentUserContext;
import org.darkroomlibrary.infrastructure.security.UserAuthCache;
import org.darkroomlibrary.domain.model.OperationLog;
import org.darkroomlibrary.service.OperationAuditService;
import org.darkroomlibrary.service.OperationLogService;
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
    private UserAuthCache userAuthCache;

    @Override
    public void record(String operation, String target, String detail) {
        Integer userId = CurrentUserContext.userId();
        if (userId == null) {
            return;
        }
        try {
            UserAuthCache.AuthUser user = userAuthCache.getActiveUser(userId).orElse(null);
            String userName = user == null ? "用户#" + userId : user.getUserName();
            operationLogService.record(OperationLog.builder()
                    .userId(userId)
                    .userName(userName)
                    .operation(operation)
                    .target(target)
                    .detail(detail)
                    .ip(resolveClientIp())
                    .build());
        } catch (Exception e) {
            log.warn("关键操作审计记录失败：operation={}, target={}", operation, target, e);
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
