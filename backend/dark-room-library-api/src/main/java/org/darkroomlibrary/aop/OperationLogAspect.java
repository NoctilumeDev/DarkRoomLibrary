package org.darkroomlibrary.aop;

import org.darkroomlibrary.service.OperationAuditService;
import org.darkroomlibrary.web.response.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;

import jakarta.annotation.Resource;
import java.lang.reflect.Method;

/**
 * 操作日志切面，自动记录受角色保护的写操作。
 */
@Slf4j
@Aspect
@Component
public class OperationLogAspect {

    @Resource
    private OperationAuditService operationAuditService;

    @AfterReturning(
            pointcut = "execution(* org.darkroomlibrary.controller.*.*(..)) && @annotation(RequireRole)",
            returning = "returnValue")
    public void logOperation(JoinPoint joinPoint, Object returnValue) {
        try {
            if (returnValue instanceof ApiResponse && ((ApiResponse<?>) returnValue).getCode() != 200) {
                return;
            }
            MethodSignature signature = (MethodSignature) joinPoint.getSignature();
            Method method = signature.getMethod();
            RequireRole protector = method.getAnnotation(RequireRole.class);
            if (protector == null || !isRoleProtected(protector)
                    || method.isAnnotationPresent(ManualAudit.class)) {
                return;
            }
            String methodName = method.getName();
            if (!isWriteOperation(methodName)) {
                return;
            }
            String operation = getOperationName(methodName);
            String target = joinPoint.getSignature().getDeclaringType().getSimpleName();
            operationAuditService.record(operation, target, null);
        } catch (Exception e) {
            log.error("操作日志记录失败", e);
        }
    }

    private boolean isWriteOperation(String methodName) {
        return methodName.startsWith("save") || methodName.startsWith("insert")
                || methodName.startsWith("update") || methodName.startsWith("batchDelete")
                || methodName.startsWith("delete") || methodName.startsWith("borrow")
                || methodName.startsWith("return") || methodName.startsWith("restore")
                || methodName.startsWith("reserve") || methodName.startsWith("cancel")
                || methodName.startsWith("renew") || methodName.startsWith("reply")
                || methodName.startsWith("report") || methodName.startsWith("cleanup");
    }

    private boolean isRoleProtected(RequireRole protector) {
        return protector.value().length > 0;
    }

    private String getOperationName(String methodName) {
        if (methodName.startsWith("save") || methodName.startsWith("insert")) return "新增";
        if (methodName.startsWith("update")) return "修改";
        if (methodName.startsWith("batchDelete") || methodName.startsWith("delete")) return "删除";
        if (methodName.startsWith("borrow")) return "借阅";
        if (methodName.startsWith("return")) return "归还";
        if (methodName.startsWith("restore")) return "恢复";
        if (methodName.startsWith("reserve")) return "预约";
        if (methodName.startsWith("cancel")) return "取消";
        if (methodName.startsWith("renew")) return "续借";
        if (methodName.startsWith("reply")) return "回复";
        if (methodName.startsWith("report")) return "举报";
        if (methodName.startsWith("cleanup")) return "清理";
        return methodName;
    }
}
