package org.darkroomlibrary.service.impl;

import org.darkroomlibrary.context.CurrentUserContext;
import org.darkroomlibrary.domain.model.ProcurementOrder;
import org.darkroomlibrary.domain.model.User;
import org.darkroomlibrary.domain.type.AccountStatus;
import org.darkroomlibrary.domain.type.UserRole;
import org.darkroomlibrary.mapper.ProcurementMessageMapper;
import org.darkroomlibrary.mapper.ProcurementOrderMapper;
import org.darkroomlibrary.mapper.UserMapper;
import org.darkroomlibrary.service.OperationAuditService;
import org.darkroomlibrary.web.dto.command.ProcurementAssignDto;
import org.darkroomlibrary.web.dto.command.ProcurementMessageReadDto;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProcurementLockOrderTest {

    @Mock
    private UserMapper userMapper;

    @Mock
    private ProcurementOrderMapper procurementOrderMapper;

    @Mock
    private ProcurementMessageMapper procurementMessageMapper;

    @Mock
    private OperationAuditService operationAuditService;

    private ProcurementServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new ProcurementServiceImpl();
        ReflectionTestUtils.setField(service, "userMapper", userMapper);
        ReflectionTestUtils.setField(service, "procurementOrderMapper", procurementOrderMapper);
        ReflectionTestUtils.setField(service, "procurementMessageMapper", procurementMessageMapper);
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

    @Test
    void readReceiptDoesNotLockTheProcurementOrder() {
        when(procurementOrderMapper.getById(11)).thenReturn(
                ProcurementOrder.builder()
                        .id(11)
                        .requesterId(2)
                        .purchaserId(9)
                        .status(1)
                        .build());
        when(procurementMessageMapper.markRead(
                eq(2), eq(11), eq(0), anyList(), any())).thenReturn(1);

        ProcurementMessageReadDto dto = new ProcurementMessageReadDto();
        dto.setOrderId(11);
        dto.setChannelType(0);
        dto.setMessageIds(List.of(101, 102));

        assertEquals(200, service.markRead(dto).getCode());

        verify(procurementOrderMapper).getById(11);
        verify(procurementOrderMapper, never()).findByIdForUpdate(11);
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
