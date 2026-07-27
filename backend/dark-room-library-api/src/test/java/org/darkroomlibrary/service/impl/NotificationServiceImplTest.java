package org.darkroomlibrary.service.impl;

import org.darkroomlibrary.domain.model.NotificationTask;
import org.darkroomlibrary.infrastructure.event.DomainEventPublisher;
import org.darkroomlibrary.mapper.NotificationTaskMapper;
import org.darkroomlibrary.utils.MailUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationServiceImplTest {

    @Mock
    private NotificationTaskMapper notificationTaskMapper;

    @Mock
    private DomainEventPublisher domainEventPublisher;

    @Mock
    private MailUtil mailUtil;

    private NotificationServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new NotificationServiceImpl();
        ReflectionTestUtils.setField(service, "notificationTaskMapper", notificationTaskMapper);
        ReflectionTestUtils.setField(service, "domainEventPublisher", domainEventPublisher);
        ReflectionTestUtils.setField(service, "mailUtil", mailUtil);
        ReflectionTestUtils.setField(service, "maxRetryCount", 8);
    }

    @Test
    @DisplayName("同一通知任务只有领取成功的消费者会发送")
    void processTaskSendsOnlyAfterAtomicClaim() {
        NotificationTask task = pendingTask();
        when(notificationTaskMapper.claimForProcessing(eq(7), any(), any(), any()))
                .thenReturn(1, 0);
        when(notificationTaskMapper.getById(7)).thenReturn(task);
        when(notificationTaskMapper.markSent(eq(7), any(), any())).thenReturn(1);

        service.processTask(7);
        service.processTask(7);

        verify(mailUtil, times(1))
                .sendSimpleOrThrow(task.getReceiverEmail(), task.getSubject(), task.getContent());
        verify(notificationTaskMapper, times(1)).markSent(eq(7), any(), any());
    }

    @Test
    @DisplayName("未领取到任务时不会读取或发送邮件")
    void processTaskSkipsWhenClaimFails() {
        when(notificationTaskMapper.claimForProcessing(eq(8), any(), any(), any())).thenReturn(0);

        service.processTask(8);

        verify(notificationTaskMapper, never()).getById(anyInt());
        verify(mailUtil, never()).sendSimpleOrThrow(any(), any(), any());
    }

    @Test
    @DisplayName("发送失败会释放租约并安排下一次补偿")
    void processTaskSchedulesRetryAfterFailure() {
        NotificationTask task = pendingTask();
        task.setRetryCount(2);
        when(notificationTaskMapper.claimForProcessing(eq(9), any(), any(), any())).thenReturn(1);
        when(notificationTaskMapper.getById(9)).thenReturn(task);
        when(notificationTaskMapper.markFailed(
                eq(9), any(), eq(2), eq(3), eq("mail unavailable"), any(), any())).thenReturn(1);
        doThrow(new IllegalStateException("mail unavailable"))
                .when(mailUtil)
                .sendSimpleOrThrow(task.getReceiverEmail(), task.getSubject(), task.getContent());

        service.processTask(9);

        verify(notificationTaskMapper).markFailed(
                eq(9), any(), eq(2), eq(3), eq("mail unavailable"),
                any(LocalDateTime.class), any(LocalDateTime.class));
        verify(notificationTaskMapper, never()).markSent(eq(9), any(), any());
    }

    @Test
    @DisplayName("达到最大重试次数后进入终止状态")
    void processTaskStopsAfterMaximumRetries() {
        NotificationTask task = pendingTask();
        task.setRetryCount(7);
        when(notificationTaskMapper.claimForProcessing(eq(10), any(), any(), any())).thenReturn(1);
        when(notificationTaskMapper.getById(10)).thenReturn(task);
        when(notificationTaskMapper.markFailed(
                eq(10), any(), eq(4), eq(8), eq("mail unavailable"), eq(null), any())).thenReturn(1);
        doThrow(new IllegalStateException("mail unavailable"))
                .when(mailUtil)
                .sendSimpleOrThrow(task.getReceiverEmail(), task.getSubject(), task.getContent());

        service.processTask(10);

        verify(notificationTaskMapper).markFailed(
                eq(10), any(), eq(4), eq(8), eq("mail unavailable"),
                eq(null), any(LocalDateTime.class));
    }

    private NotificationTask pendingTask() {
        return NotificationTask.builder()
                .id(7)
                .receiverEmail("reader@example.com")
                .subject("到期提醒")
                .content("请及时归还图书")
                .status(0)
                .retryCount(0)
                .build();
    }
}
