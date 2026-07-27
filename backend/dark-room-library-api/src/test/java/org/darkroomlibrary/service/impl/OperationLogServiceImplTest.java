package org.darkroomlibrary.service.impl;

import org.darkroomlibrary.BaseTest;
import org.darkroomlibrary.domain.model.OperationLog;
import org.darkroomlibrary.mapper.OperationLogMapper;
import org.darkroomlibrary.service.OperationAuditService;
import org.darkroomlibrary.service.OperationLogService;
import org.darkroomlibrary.domain.model.User;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import jakarta.annotation.Resource;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class OperationLogServiceImplTest extends BaseTest {

    @Resource
    private OperationLogService operationLogService;

    @Resource
    private OperationAuditService operationAuditService;

    @Resource
    private OperationLogMapper operationLogMapper;

    @Resource
    private PlatformTransactionManager transactionManager;

    @Test
    void writerUsesIndependentTransaction() {
        long before = operationLogMapper.selectCount(null);
        TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);

        transactionTemplate.executeWithoutResult(status -> {
            operationLogService.record(testLog("提交"));
            assertEquals(before + 1, operationLogMapper.selectCount(null));
            status.setRollbackOnly();
        });

        assertEquals(before + 1, operationLogMapper.selectCount(null));
    }

    @Test
    void auditIsPersistedOnlyAfterBusinessCommit() {
        User user = createTestUser(
                "audit_commit_" + System.nanoTime(),
                "提交审计用户",
                "audit_commit_" + System.nanoTime() + "@example.test");
        setCurrentUser(user.getId(), user.getUserRole());
        long before = operationLogMapper.selectCount(null);
        TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);

        transactionTemplate.executeWithoutResult(status -> {
            operationAuditService.record("修改", "事务审计", "提交");
            assertEquals(before, operationLogMapper.selectCount(null));
        });

        assertEquals(before + 1, operationLogMapper.selectCount(null));
        clearContext();
    }

    @Test
    void auditIsSkippedWhenBusinessTransactionRollsBack() {
        User user = createTestUser(
                "audit_rollback_" + System.nanoTime(),
                "回滚审计用户",
                "audit_rollback_" + System.nanoTime() + "@example.test");
        setCurrentUser(user.getId(), user.getUserRole());
        long before = operationLogMapper.selectCount(null);
        TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);

        transactionTemplate.executeWithoutResult(status -> {
            operationAuditService.record("修改", "事务审计", "回滚");
            status.setRollbackOnly();
        });

        assertEquals(before, operationLogMapper.selectCount(null));
        clearContext();
    }

    private OperationLog testLog(String detail) {
        return OperationLog.builder()
                .userId(1)
                .userName("测试用户")
                .operation("修改")
                .target("事务审计")
                .detail(detail)
                .ip("127.0.0.1")
                .build();
    }
}
