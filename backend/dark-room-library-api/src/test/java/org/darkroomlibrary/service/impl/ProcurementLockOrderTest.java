package org.darkroomlibrary.service.impl;

import org.darkroomlibrary.context.CurrentUserContext;
import org.darkroomlibrary.domain.model.ProcurementOrder;
import org.darkroomlibrary.domain.model.User;
import org.darkroomlibrary.domain.type.AccountStatus;
import org.darkroomlibrary.domain.type.UserRole;
import org.darkroomlibrary.mapper.ProcurementOrderMapper;
import org.darkroomlibrary.mapper.UserMapper;
import org.darkroomlibrary.service.OperationAuditService;
import org.darkroomlibrary.web.dto.command.ProcurementAssignDto;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.inOrder;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProcurementLockOrderTest {

    @Mock
    private UserMapper userMapper;

    @Mock
    private ProcurementOrderMapper procurementOrderMapper;

    @Mock
    private OperationAuditService operationAuditService;

    private ProcurementServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new ProcurementServiceImpl();
        ReflectionTestUtils.setField(service, "userMapper", userMapper);
        ReflectionTestUtils.setField(service, "procurementOrderMapper", procurementOrderMapper);
        ReflectionTestUtils.setField(service, "operationAuditService", operationAuditService);
        CurrentUserContext.bind(2, UserRole.ADMIN.code());
    }

    @AfterEach
    void tearDown() {
        CurrentUserContext.clear();
    }

    @Test
    void assigneeUsersAreLockedBeforeTheOrderRow() {
        User admin = activeUser(2, UserRole.ADMIN.code());
        User purchaser = activeUser(9, UserRole.ACQUISITIONS.code());
        when(userMapper.findByIdForUpdate(2)).thenReturn(admin);
        when(userMapper.findByIdForUpdate(9)).thenReturn(purchaser);
        when(procurementOrderMapper.findByIdForUpdate(11)).thenReturn(
                ProcurementOrder.builder()
                        .id(11)
                        .requesterId(2)
                        .status(0)
                        .bookId(5)
                        .bookName("test")
                        .requestCount(1)
                        .build());
        when(procurementOrderMapper.update(any(ProcurementOrder.class))).thenReturn(1);

        ProcurementAssignDto dto = new ProcurementAssignDto();
        dto.setOrderId(11);
        dto.setUserId(9);

        assertEquals(200, service.assignPurchaser(dto).getCode());

        InOrder order = inOrder(userMapper, procurementOrderMapper);
        order.verify(userMapper).findByIdForUpdate(2);
        order.verify(userMapper).findByIdForUpdate(9);
        order.verify(procurementOrderMapper).findByIdForUpdate(11);
    }

    private User activeUser(Integer id, Integer role) {
        return User.builder()
                .id(id)
                .userName("user-" + id)
                .userRole(role)
                .accountStatus(AccountStatus.NORMAL.code())
                .isLogin(false)
                .build();
    }
}
