package org.darkroomlibrary.service.impl;

import org.darkroomlibrary.BaseTest;
import org.darkroomlibrary.mapper.OperationLogMapper;
import org.darkroomlibrary.mapper.ProcurementOrderMapper;
import org.darkroomlibrary.web.response.ApiResponse;
import org.darkroomlibrary.web.dto.query.ProcurementMessagePageQuery;
import org.darkroomlibrary.web.dto.query.ProcurementOrderPageQuery;
import org.darkroomlibrary.web.dto.command.ProcurementAssignDto;
import org.darkroomlibrary.web.dto.command.ProcurementLogisticsUpdateDto;
import org.darkroomlibrary.web.dto.command.ProcurementMessageDto;
import org.darkroomlibrary.web.dto.command.ProcurementMessageReadDto;
import org.darkroomlibrary.web.dto.command.ProcurementOrderCreateDto;
import org.darkroomlibrary.web.dto.command.ProcurementStatusUpdateDto;
import org.darkroomlibrary.domain.type.LoginStatus;
import org.darkroomlibrary.domain.type.UserRole;
import org.darkroomlibrary.domain.type.MuteStatus;
import org.darkroomlibrary.domain.model.Book;
import org.darkroomlibrary.domain.model.OperationLog;
import org.darkroomlibrary.domain.model.ProcurementOrder;
import org.darkroomlibrary.domain.model.User;
import org.darkroomlibrary.web.view.ProcurementMessageView;
import org.darkroomlibrary.web.view.ProcurementOrderView;
import org.darkroomlibrary.service.ProcurementService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;

import jakarta.annotation.Resource;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 采购与物流协作服务测试
 */
@SpringBootTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
public class ProcurementServiceImplTest extends BaseTest {

    @Resource
    private ProcurementService procurementService;

    @Resource
    private ProcurementOrderMapper procurementOrderMapper;

    @Resource
    private OperationLogMapper operationLogMapper;

    @AfterEach
    void tearDown() {
        clearContext();
    }

    @Test
    @DisplayName("采购员物流员闭环协作并在入库时补充库存")
    void testProcurementWorkflowAndMessages() {
        User admin = createRoleUser("proc_admin", "采购管理员", UserRole.ADMIN.code());
        User purchaser = createRoleUser("proc_buyer", "采购员甲", UserRole.ACQUISITIONS.code());
        User logistics = createRoleUser("proc_logistics", "物流员甲", UserRole.LOGISTICS.code());
        Book book = createTestBook("低库存图书", "作者甲", 2);
        Integer beforeTotal = book.getTotalCount();
        Integer beforeAvailable = book.getAvailableCount();

        setCurrentUser(admin.getId(), admin.getUserRole());
        ProcurementOrderCreateDto createDTO = new ProcurementOrderCreateDto();
        createDTO.setBookId(book.getId());
        createDTO.setRequestCount(5);
        createDTO.setRequestNote("低库存补书");
        ApiResponse<Void> createResult = procurementService.save(createDTO);
        assertEquals(200, createResult.getCode());

        ProcurementOrderView order = queryFirstOrder(book.getId());
        assertEquals(0, order.getStatus());

        ProcurementAssignDto assignPurchaser = new ProcurementAssignDto();
        assignPurchaser.setOrderId(order.getId());
        assignPurchaser.setUserId(purchaser.getId());
        assertEquals(200, procurementService.assignPurchaser(assignPurchaser).getCode());

        ProcurementMessageDto adminMessage = new ProcurementMessageDto();
        adminMessage.setOrderId(order.getId());
        adminMessage.setChannelType(0);
        adminMessage.setReceiverId(purchaser.getId());
        adminMessage.setContent("请处理这批补书。");
        assertEquals(200, procurementService.sendMessage(adminMessage).getCode());

        setCurrentUser(purchaser.getId(), purchaser.getUserRole());
        ApiResponse<Map<String, Object>> unreadBefore = procurementService.unreadCount(order.getId());
        assertEquals(1, unreadBefore.getData().get("total"));
        ProcurementMessagePageQuery unreadQuery = new ProcurementMessagePageQuery();
        unreadQuery.setOrderId(order.getId());
        unreadQuery.setChannelType(0);
        unreadQuery.setCurrent(0);
        unreadQuery.setSize(10);
        ProcurementMessageReadDto readDto = new ProcurementMessageReadDto();
        readDto.setOrderId(order.getId());
        readDto.setChannelType(0);
        readDto.setMessageIds(procurementService.queryMessages(unreadQuery).getData().stream()
                .map(ProcurementMessageView::getId)
                .toList());
        assertEquals(200, procurementService.markRead(readDto).getCode());
        ApiResponse<Map<String, Object>> unreadAfter = procurementService.unreadCount(order.getId());
        assertEquals(0, unreadAfter.getData().get("total"));

        ProcurementStatusUpdateDto purchaseStatus = new ProcurementStatusUpdateDto();
        purchaseStatus.setId(order.getId());
        purchaseStatus.setStatus(1);
        purchaseStatus.setPurchaseNote("采购处理中");
        assertEquals(200, procurementService.updateStatus(purchaseStatus).getCode());
        purchaseStatus.setStatus(2);
        purchaseStatus.setPurchaseNote("已下单");
        assertEquals(200, procurementService.updateStatus(purchaseStatus).getCode());
        purchaseStatus.setStatus(3);
        assertEquals(400, procurementService.updateStatus(purchaseStatus).getCode());

        ProcurementAssignDto assignLogistics = new ProcurementAssignDto();
        assignLogistics.setOrderId(order.getId());
        assignLogistics.setUserId(logistics.getId());
        assertEquals(200, procurementService.assignLogistics(assignLogistics).getCode());

        ProcurementMessageDto buyerMessage = new ProcurementMessageDto();
        buyerMessage.setOrderId(order.getId());
        buyerMessage.setChannelType(1);
        buyerMessage.setReceiverId(logistics.getId());
        buyerMessage.setContent("到货后同步入库状态。");
        assertEquals(200, procurementService.sendMessage(buyerMessage).getCode());

        setCurrentUser(logistics.getId(), logistics.getUserRole());
        ProcurementOrderPageQuery logisticsQuery = new ProcurementOrderPageQuery();
        logisticsQuery.setCurrent(0);
        logisticsQuery.setSize(10);
        ApiResponse<List<ProcurementOrderView>> logisticsOrders = procurementService.query(logisticsQuery);
        assertEquals(200, logisticsOrders.getCode());
        assertEquals(1, logisticsOrders.getData().size());

        ProcurementLogisticsUpdateDto logisticsUpdate = new ProcurementLogisticsUpdateDto();
        logisticsUpdate.setOrderId(order.getId());
        logisticsUpdate.setTrackingNo("EXP-001");
        logisticsUpdate.setCarrier("测试物流");
        logisticsUpdate.setStatus(1);
        logisticsUpdate.setRemark("运输中");
        assertEquals(200, procurementService.updateLogistics(logisticsUpdate).getCode());
        logisticsUpdate.setStatus(2);
        logisticsUpdate.setRemark("已到馆");
        assertEquals(200, procurementService.updateLogistics(logisticsUpdate).getCode());
        logisticsUpdate.setStatus(3);
        logisticsUpdate.setRemark("已入库");
        assertEquals(200, procurementService.updateLogistics(logisticsUpdate).getCode());

        Book afterBook = bookMapper.getById(book.getId());
        assertEquals(beforeTotal + 5, afterBook.getTotalCount());
        assertEquals(beforeAvailable + 5, afterBook.getAvailableCount());
        ProcurementOrder afterOrder = procurementOrderMapper.getById(order.getId());
        assertEquals(5, afterOrder.getStatus());
        assertTrue(afterOrder.getStockApplied());

        assertEquals(200, procurementService.updateLogistics(logisticsUpdate).getCode());
        Book afterRepeatedWarehouse = bookMapper.getById(book.getId());
        assertEquals(beforeTotal + 5, afterRepeatedWarehouse.getTotalCount());
        assertEquals(beforeAvailable + 5, afterRepeatedWarehouse.getAvailableCount());

        setCurrentUser(purchaser.getId(), purchaser.getUserRole());
        ProcurementStatusUpdateDto cancelAfterWarehouse = new ProcurementStatusUpdateDto();
        cancelAfterWarehouse.setId(order.getId());
        cancelAfterWarehouse.setStatus(7);
        cancelAfterWarehouse.setPurchaseNote("入库后不可取消");
        assertEquals(400, procurementService.updateStatus(cancelAfterWarehouse).getCode());

        List<OperationLog> orderLogs = auditLogsForOrder(order.getId());
        assertTrue(hasAudit(orderLogs, "新增", "采购单", "创建采购单"));
        assertTrue(hasAudit(orderLogs, "指派", "采购单", "采购员：未指派"));
        assertTrue(hasAudit(orderLogs, "流转", "采购单", "待采购 -> 采购中"));
        assertTrue(hasAudit(orderLogs, "流转", "采购单", "采购中 -> 已下单"));
        assertTrue(hasAudit(orderLogs, "指派", "物流任务", "物流员：未指派"));
        assertTrue(hasAudit(orderLogs, "流转", "物流任务", "待接收 -> 运输中"));
        assertTrue(hasAudit(orderLogs, "流转", "物流任务", "运输中 -> 已到馆"));
        assertTrue(hasAudit(orderLogs, "入库", "物流任务", "已到馆 -> 已入库"));
        assertEquals(1, orderLogs.stream()
                .filter(log -> "库存补充".equals(log.getOperation())
                        && "图书库存".equals(log.getTarget()))
                .count());

        setCurrentUser(admin.getId(), admin.getUserRole());
        ProcurementMessageDto invalidMessage = new ProcurementMessageDto();
        invalidMessage.setOrderId(order.getId());
        invalidMessage.setChannelType(1);
        invalidMessage.setReceiverId(logistics.getId());
        invalidMessage.setContent("管理员不能直接进入物流通道。");
        assertEquals(400, procurementService.sendMessage(invalidMessage).getCode());
    }

    @Test
    @DisplayName("采购单认领和取消均记录状态审计")
    void testClaimAndCancelAudit() {
        User admin = createRoleUser("proc_claim_admin", "认领管理员", UserRole.ADMIN.code());
        User purchaser = createRoleUser("proc_claim_buyer", "认领采购员", UserRole.ACQUISITIONS.code());
        Book book = createTestBook("认领取消测试图书", "作者认领", 1);

        setCurrentUser(admin.getId(), admin.getUserRole());
        ProcurementOrderCreateDto createDTO = new ProcurementOrderCreateDto();
        createDTO.setBookId(book.getId());
        createDTO.setRequestCount(2);
        assertEquals(200, procurementService.save(createDTO).getCode());
        Integer orderId = queryFirstOrder(book.getId()).getId();

        setCurrentUser(purchaser.getId(), purchaser.getUserRole());
        assertEquals(200, procurementService.claim(orderId).getCode());

        ProcurementStatusUpdateDto cancelDTO = new ProcurementStatusUpdateDto();
        cancelDTO.setId(orderId);
        cancelDTO.setStatus(3);
        cancelDTO.setPurchaseNote("禁止跳级");
        assertEquals(400, procurementService.updateStatus(cancelDTO).getCode());

        cancelDTO.setStatus(7);
        cancelDTO.setPurchaseNote("预算调整");
        assertEquals(200, procurementService.updateStatus(cancelDTO).getCode());

        List<OperationLog> orderLogs = auditLogsForOrder(orderId);
        assertTrue(hasAudit(orderLogs, "认领", "采购单", "待采购 -> 采购中"));
        assertTrue(hasAudit(orderLogs, "取消", "采购单", "采购中 -> 已取消"));
    }

    @Test
    @DisplayName("采购协作消息按通道查询")
    void testMessageQueryByChannel() {
        User admin = createRoleUser("proc_admin2", "采购管理员2", UserRole.ADMIN.code());
        User purchaser = createRoleUser("proc_buyer2", "采购员乙", UserRole.ACQUISITIONS.code());
        Book book = createTestBook("消息测试图书", "作者乙", 1);

        setCurrentUser(admin.getId(), admin.getUserRole());
        ProcurementOrderCreateDto createDTO = new ProcurementOrderCreateDto();
        createDTO.setBookId(book.getId());
        createDTO.setRequestCount(2);
        createDTO.setPurchaserId(purchaser.getId());
        assertEquals(200, procurementService.save(createDTO).getCode());
        Integer orderId = queryFirstOrder(book.getId()).getId();

        ProcurementMessageDto messageDTO = new ProcurementMessageDto();
        messageDTO.setOrderId(orderId);
        messageDTO.setChannelType(0);
        messageDTO.setReceiverId(purchaser.getId());
        messageDTO.setContent("请确认采购数量。");
        assertEquals(200, procurementService.sendMessage(messageDTO).getCode());

        ProcurementMessagePageQuery queryDTO = new ProcurementMessagePageQuery();
        queryDTO.setOrderId(orderId);
        queryDTO.setChannelType(0);
        queryDTO.setCurrent(0);
        queryDTO.setSize(10);
        ApiResponse<List<ProcurementMessageView>> queryResult = procurementService.queryMessages(queryDTO);
        assertEquals(200, queryResult.getCode());
        assertEquals(1, queryResult.getData().size());
        assertEquals("请确认采购数量。", queryResult.getData().get(0).getContent());
    }

    @Test
    @DisplayName("普通管理员隔离采购单和沟通记录，超级管理员可全局审计")
    void testAdminIsolationAndSuperAdminAudit() {
        User adminA = createRoleUser("proc_admin_a", "采购管理员A", UserRole.ADMIN.code());
        User adminB = createRoleUser("proc_admin_b", "采购管理员B", UserRole.ADMIN.code());
        User superAdmin = createRoleUser("proc_super_admin", "超级管理员", UserRole.SUPER_ADMIN.code());
        User purchaserA = createRoleUser("proc_buyer_a", "采购员A", UserRole.ACQUISITIONS.code());
        User purchaserB = createRoleUser("proc_buyer_b", "采购员B", UserRole.ACQUISITIONS.code());
        Book bookA = createTestBook("隔离测试图书A", "作者A", 1);
        Book bookB = createTestBook("隔离测试图书B", "作者B", 1);

        Integer orderAId = createOrder(adminA, bookA, purchaserA, 3);
        Integer orderBId = createOrder(adminB, bookB, purchaserB, 4);

        setCurrentUser(adminA.getId(), adminA.getUserRole());
        ProcurementOrderPageQuery adminQuery = new ProcurementOrderPageQuery();
        adminQuery.setCurrent(0);
        adminQuery.setSize(20);
        ApiResponse<List<ProcurementOrderView>> adminOrders = procurementService.query(adminQuery);
        assertEquals(200, adminOrders.getCode());
        assertTrue(adminOrders.getData().stream().anyMatch(item -> orderAId.equals(item.getId())));
        assertTrue(adminOrders.getData().stream().noneMatch(item -> orderBId.equals(item.getId())));

        ProcurementMessageDto adminAMessage = new ProcurementMessageDto();
        adminAMessage.setOrderId(orderAId);
        adminAMessage.setChannelType(0);
        adminAMessage.setReceiverId(purchaserA.getId());
        adminAMessage.setContent("A 管理员创建的采购沟通。");
        assertEquals(200, procurementService.sendMessage(adminAMessage).getCode());

        setCurrentUser(adminB.getId(), adminB.getUserRole());
        ProcurementMessagePageQuery messageQuery = new ProcurementMessagePageQuery();
        messageQuery.setOrderId(orderAId);
        messageQuery.setChannelType(0);
        messageQuery.setCurrent(0);
        messageQuery.setSize(10);
        assertEquals(400, procurementService.queryMessages(messageQuery).getCode());

        ProcurementMessageDto crossMessage = new ProcurementMessageDto();
        crossMessage.setOrderId(orderAId);
        crossMessage.setChannelType(0);
        crossMessage.setReceiverId(purchaserA.getId());
        crossMessage.setContent("B 管理员不能介入 A 的采购单。");
        assertEquals(400, procurementService.sendMessage(crossMessage).getCode());

        setCurrentUser(purchaserA.getId(), purchaserA.getUserRole());
        ProcurementOrderPageQuery purchaserQuery = new ProcurementOrderPageQuery();
        purchaserQuery.setCurrent(0);
        purchaserQuery.setSize(20);
        ApiResponse<List<ProcurementOrderView>> purchaserOrders = procurementService.query(purchaserQuery);
        assertEquals(200, purchaserOrders.getCode());
        assertTrue(purchaserOrders.getData().stream().anyMatch(item -> orderAId.equals(item.getId())));
        assertTrue(purchaserOrders.getData().stream().noneMatch(item -> orderBId.equals(item.getId())));

        setCurrentUser(superAdmin.getId(), superAdmin.getUserRole());
        ProcurementOrderPageQuery superQuery = new ProcurementOrderPageQuery();
        superQuery.setCurrent(0);
        superQuery.setSize(50);
        ApiResponse<List<ProcurementOrderView>> superOrders = procurementService.query(superQuery);
        assertEquals(200, superOrders.getCode());
        assertTrue(superOrders.getData().stream().anyMatch(item -> orderAId.equals(item.getId())));
        assertTrue(superOrders.getData().stream().anyMatch(item -> orderBId.equals(item.getId())));

        ApiResponse<List<ProcurementMessageView>> auditMessages = procurementService.queryMessages(messageQuery);
        assertEquals(200, auditMessages.getCode());
        assertEquals(1, auditMessages.getData().size());
        assertEquals("A 管理员创建的采购沟通。", auditMessages.getData().get(0).getContent());
    }

    @Test
    @DisplayName("馆务协调员可跨管理员协调采购单但不能进入采购物流专属通道")
    void testCoordinatorAdminCanCoordinateAcrossAdminOrders() {
        User adminA = createRoleUser("proc_admin_c", "采购管理员C", UserRole.ADMIN.code());
        User adminB = createRoleUser("proc_admin_d", "采购管理员D", UserRole.ADMIN.code());
        User coordinatorAdmin = createCoordinatorAdmin("proc_coordinator_admin", "馆务协调员");
        User purchaserA = createRoleUser("proc_buyer_c", "采购员C", UserRole.ACQUISITIONS.code());
        User purchaserB = createRoleUser("proc_buyer_d", "采购员D", UserRole.ACQUISITIONS.code());
        User logistics = createRoleUser("proc_logistics_b", "物流员B", UserRole.LOGISTICS.code());
        Book bookA = createTestBook("馆务协调图书A", "作者C", 1);
        Book bookB = createTestBook("馆务协调图书B", "作者D", 1);

        Integer orderAId = createOrder(adminA, bookA, purchaserA, 3);
        Integer orderBId = createOrder(adminB, bookB, purchaserB, 4);

        setCurrentUser(coordinatorAdmin.getId(), coordinatorAdmin.getUserRole());
        ProcurementOrderPageQuery coordinatorQuery = new ProcurementOrderPageQuery();
        coordinatorQuery.setCurrent(0);
        coordinatorQuery.setSize(50);
        ApiResponse<List<ProcurementOrderView>> coordinatorOrders = procurementService.query(coordinatorQuery);
        assertEquals(200, coordinatorOrders.getCode());
        assertTrue(coordinatorOrders.getData().stream().anyMatch(item -> orderAId.equals(item.getId())));
        assertTrue(coordinatorOrders.getData().stream().anyMatch(item -> orderBId.equals(item.getId())));

        ProcurementAssignDto reassign = new ProcurementAssignDto();
        reassign.setOrderId(orderBId);
        reassign.setUserId(purchaserA.getId());
        assertEquals(200, procurementService.assignPurchaser(reassign).getCode());

        ProcurementMessageDto coordinatorMessage = new ProcurementMessageDto();
        coordinatorMessage.setOrderId(orderAId);
        coordinatorMessage.setChannelType(0);
        coordinatorMessage.setReceiverId(purchaserA.getId());
        coordinatorMessage.setContent("请优先确认这批采购。");
        assertEquals(200, procurementService.sendMessage(coordinatorMessage).getCode());

        ProcurementMessageDto invalidLogisticsMessage = new ProcurementMessageDto();
        invalidLogisticsMessage.setOrderId(orderAId);
        invalidLogisticsMessage.setChannelType(1);
        invalidLogisticsMessage.setReceiverId(logistics.getId());
        invalidLogisticsMessage.setContent("管理员仍不能直接进入物流通道。");
        assertEquals(400, procurementService.sendMessage(invalidLogisticsMessage).getCode());
    }

    @Test
    @DisplayName("并发认领采购单时只能有一个采购员成功")
    void testConcurrentClaimOnlyOnePurchaserSucceeds() throws Exception {
        String suffix = String.valueOf(System.nanoTime());
        User admin = createRoleUser("claim_admin_" + suffix, "并发认领管理员", UserRole.ADMIN.code());
        User firstPurchaser = createRoleUser(
                "claim_buyer_a_" + suffix, "并发采购员甲", UserRole.ACQUISITIONS.code());
        User secondPurchaser = createRoleUser(
                "claim_buyer_b_" + suffix, "并发采购员乙", UserRole.ACQUISITIONS.code());
        Book book = createTestBook("并发认领图书-" + suffix, "并发作者", 1);

        setCurrentUser(admin.getId(), admin.getUserRole());
        ProcurementOrderCreateDto createDto = new ProcurementOrderCreateDto();
        createDto.setBookId(book.getId());
        createDto.setRequestCount(2);
        assertEquals(200, procurementService.save(createDto).getCode());
        Integer orderId = queryFirstOrder(book.getId()).getId();
        clearContext();

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger successCount = new AtomicInteger();
        List<Future<?>> futures = List.of(
                submitClaim(executor, start, firstPurchaser, orderId, successCount),
                submitClaim(executor, start, secondPurchaser, orderId, successCount)
        );

        start.countDown();
        for (Future<?> future : futures) {
            future.get(5, TimeUnit.SECONDS);
        }
        executor.shutdown();
        assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));

        ProcurementOrder stored = procurementOrderMapper.getById(orderId);
        assertEquals(1, successCount.get());
        assertEquals(1, stored.getStatus());
        assertTrue(stored.getPurchaserId().equals(firstPurchaser.getId())
                || stored.getPurchaserId().equals(secondPurchaser.getId()));
    }

    @Test
    @DisplayName("未下单不能分配物流，取消后物流不能继续推进或入库")
    void testLogisticsCannotBypassOrderLifecycle() {
        String suffix = String.valueOf(System.nanoTime());
        User admin = createRoleUser("lifecycle_admin_" + suffix, "流程管理员", UserRole.ADMIN.code());
        User purchaser = createRoleUser(
                "lifecycle_buyer_" + suffix, "流程采购员", UserRole.ACQUISITIONS.code());
        User logistics = createRoleUser(
                "lifecycle_logistics_" + suffix, "流程物流员", UserRole.LOGISTICS.code());
        Book book = createTestBook("物流流程图书-" + suffix, "流程作者", 2);
        Integer initialTotal = book.getTotalCount();
        Integer initialAvailable = book.getAvailableCount();
        Integer orderId = createOrder(admin, book, purchaser, 4);

        setCurrentUser(purchaser.getId(), purchaser.getUserRole());
        ProcurementAssignDto assignLogistics = new ProcurementAssignDto();
        assignLogistics.setOrderId(orderId);
        assignLogistics.setUserId(logistics.getId());
        assertEquals(400, procurementService.assignLogistics(assignLogistics).getCode());

        ProcurementStatusUpdateDto status = new ProcurementStatusUpdateDto();
        status.setId(orderId);
        status.setStatus(1);
        assertEquals(200, procurementService.updateStatus(status).getCode());
        status.setStatus(2);
        assertEquals(200, procurementService.updateStatus(status).getCode());
        assertEquals(200, procurementService.assignLogistics(assignLogistics).getCode());

        status.setStatus(7);
        status.setPurchaseNote("<b>预算取消</b>");
        assertEquals(200, procurementService.updateStatus(status).getCode());

        setCurrentUser(logistics.getId(), logistics.getUserRole());
        ProcurementLogisticsUpdateDto logisticsUpdate = new ProcurementLogisticsUpdateDto();
        logisticsUpdate.setOrderId(orderId);
        logisticsUpdate.setStatus(1);
        logisticsUpdate.setCarrier("不应执行的物流");
        assertEquals(400, procurementService.updateLogistics(logisticsUpdate).getCode());

        ProcurementOrder storedOrder = procurementOrderMapper.getById(orderId);
        Book storedBook = bookMapper.getById(book.getId());
        assertEquals(7, storedOrder.getStatus());
        assertFalse(storedOrder.getStockApplied());
        assertEquals(initialTotal, storedBook.getTotalCount());
        assertEquals(initialAvailable, storedBook.getAvailableCount());
    }

    @Test
    @DisplayName("采购说明和物流字段按纯文本保存")
    void testProcurementTextFieldsAreSanitized() {
        String suffix = String.valueOf(System.nanoTime());
        User admin = createRoleUser("sanitize_admin_" + suffix, "净化管理员", UserRole.ADMIN.code());
        Book book = createTestBook("净化测试图书-" + suffix, "净化作者", 1);
        setCurrentUser(admin.getId(), admin.getUserRole());

        ProcurementOrderCreateDto createDto = new ProcurementOrderCreateDto();
        createDto.setBookId(book.getId());
        createDto.setRequestCount(1);
        createDto.setRequestNote("<b>补充馆藏</b><script>alert(1)</script>");
        assertEquals(200, procurementService.save(createDto).getCode());

        ProcurementOrderView stored = queryFirstOrder(book.getId());
        assertNotNull(stored.getRequestNote());
        assertFalse(stored.getRequestNote().contains("<"));
        assertTrue(stored.getRequestNote().contains("补充馆藏"));
    }

    private ProcurementOrderView queryFirstOrder(Integer bookId) {
        ProcurementOrderPageQuery queryDTO = new ProcurementOrderPageQuery();
        queryDTO.setBookId(bookId);
        queryDTO.setCurrent(0);
        queryDTO.setSize(10);
        ApiResponse<List<ProcurementOrderView>> queryResult = procurementService.query(queryDTO);
        assertEquals(200, queryResult.getCode());
        assertFalse(queryResult.getData().isEmpty());
        return queryResult.getData().get(0);
    }

    private List<OperationLog> auditLogsForOrder(Integer orderId) {
        String orderToken = "采购单ID=" + orderId + "，";
        return operationLogMapper.selectList(null).stream()
                .filter(log -> log.getDetail() != null && log.getDetail().contains(orderToken))
                .collect(java.util.stream.Collectors.toList());
    }

    private boolean hasAudit(List<OperationLog> logs,
                             String operation,
                             String target,
                             String detailText) {
        return logs.stream().anyMatch(log ->
                operation.equals(log.getOperation())
                        && target.equals(log.getTarget())
                        && log.getDetail().contains(detailText));
    }

    private Integer createOrder(User admin, Book book, User purchaser, Integer requestCount) {
        setCurrentUser(admin.getId(), admin.getUserRole());
        ProcurementOrderCreateDto createDTO = new ProcurementOrderCreateDto();
        createDTO.setBookId(book.getId());
        createDTO.setRequestCount(requestCount);
        createDTO.setPurchaserId(purchaser.getId());
        ApiResponse<Void> result = procurementService.save(createDTO);
        assertEquals(200, result.getCode());
        return queryFirstOrder(book.getId()).getId();
    }

    private User createRoleUser(String account, String userName, Integer role) {
        User user = User.builder()
                .userAccount(account)
                .userName(userName)
                .userPwd(encodePassword("Test@123456"))
                .userEmail(account + "@example.test")
                .userRole(role)
                .isCoordinatorAdmin(false)
                .isLogin(LoginStatus.ACTIVE.disabled())
                .isWord(MuteStatus.ACTIVE.muted())
                .createTime(LocalDateTime.now())
                .build();
        userMapper.insert(user);
        return user;
    }

    private User createCoordinatorAdmin(String account, String userName) {
        User user = createRoleUser(account, userName, UserRole.ADMIN.code());
        userMapper.update(User.builder().id(user.getId()).isCoordinatorAdmin(true).build());
        user.setIsCoordinatorAdmin(true);
        return user;
    }

    private Future<?> submitClaim(ExecutorService executor,
                                  CountDownLatch start,
                                  User purchaser,
                                  Integer orderId,
                                  AtomicInteger successCount) {
        return executor.submit(() -> {
            start.await();
            setCurrentUser(purchaser.getId(), purchaser.getUserRole());
            try {
                ApiResponse<Void> result = procurementService.claim(orderId);
                if (result != null && Integer.valueOf(200).equals(result.getCode())) {
                    successCount.incrementAndGet();
                }
            } finally {
                clearContext();
            }
            return null;
        });
    }
}
