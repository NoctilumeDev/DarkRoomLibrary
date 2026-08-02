package org.darkroomlibrary.service.impl;

import org.darkroomlibrary.context.CurrentUserContext;
import org.darkroomlibrary.mapper.BookMapper;
import org.darkroomlibrary.mapper.ProcurementLogisticsMapper;
import org.darkroomlibrary.mapper.ProcurementMessageMapper;
import org.darkroomlibrary.mapper.ProcurementOrderMapper;
import org.darkroomlibrary.mapper.UserMapper;
import org.darkroomlibrary.web.response.ApiResponse;
import org.darkroomlibrary.web.response.PageResponse;
import org.darkroomlibrary.web.dto.query.ProcurementMessagePageQuery;
import org.darkroomlibrary.web.dto.query.ProcurementOrderPageQuery;
import org.darkroomlibrary.web.dto.command.ProcurementAssignDto;
import org.darkroomlibrary.web.dto.command.ProcurementLogisticsUpdateDto;
import org.darkroomlibrary.web.dto.command.ProcurementMessageDto;
import org.darkroomlibrary.web.dto.command.ProcurementMessageReadDto;
import org.darkroomlibrary.web.dto.command.ProcurementOrderCreateDto;
import org.darkroomlibrary.web.dto.command.ProcurementStatusUpdateDto;
import org.darkroomlibrary.domain.type.UserRole;
import org.darkroomlibrary.domain.type.AccountStatus;
import org.darkroomlibrary.domain.model.Book;
import org.darkroomlibrary.domain.model.ProcurementLogistics;
import org.darkroomlibrary.domain.model.ProcurementMessage;
import org.darkroomlibrary.domain.model.ProcurementOrder;
import org.darkroomlibrary.domain.model.User;
import org.darkroomlibrary.web.view.ProcurementMessageView;
import org.darkroomlibrary.web.view.ProcurementOrderView;
import org.darkroomlibrary.web.view.OrderUnreadSummary;
import org.darkroomlibrary.service.OperationAuditService;
import org.darkroomlibrary.service.ProcurementService;
import org.darkroomlibrary.service.ReservationWorkflowService;
import org.darkroomlibrary.service.support.RecommendationSourceVersionService;
import org.darkroomlibrary.utils.ContentSanitizer;
import org.darkroomlibrary.utils.IdListUtils;
import org.darkroomlibrary.utils.TransactionCallbacks;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.interceptor.TransactionAspectSupport;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import jakarta.annotation.Resource;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 采购协作服务实现
 */
@Service
@Slf4j
public class ProcurementServiceImpl implements ProcurementService {

    private static final int ORDER_PENDING = 0;
    private static final int ORDER_PURCHASING = 1;
    private static final int ORDER_PLACED = 2;
    private static final int ORDER_SHIPPED = 3;
    private static final int ORDER_ARRIVED = 4;
    private static final int ORDER_WAREHOUSED = 5;
    private static final int ORDER_COMPLETED = 6;
    private static final int ORDER_CANCELED = 7;

    private static final int LOGISTICS_PENDING = 0;
    private static final int LOGISTICS_TRANSIT = 1;
    private static final int LOGISTICS_ARRIVED = 2;
    private static final int LOGISTICS_WAREHOUSED = 3;

    private static final int CHANNEL_ADMIN_PURCHASER = 0;
    private static final int CHANNEL_PURCHASER_LOGISTICS = 1;

    @Resource
    private ProcurementOrderMapper procurementOrderMapper;

    @Resource
    private ProcurementLogisticsMapper procurementLogisticsMapper;

    @Resource
    private ProcurementMessageMapper procurementMessageMapper;

    @Resource
    private BookMapper bookMapper;

    @Resource
    private UserMapper userMapper;

    @Resource
    private OperationAuditService operationAuditService;

    @Resource
    private ReservationWorkflowService reservationWorkflowService;

    @Resource
    private RecommendationSourceVersionService recommendationSourceVersionService;

    @Override
    @Transactional
    public ApiResponse<Void> save(ProcurementOrderCreateDto dto) {
        if (dto == null || dto.getBookId() == null) {
            return ApiResponse.error("请选择需要采购的图书");
        }
        if (dto.getRequestCount() == null || dto.getRequestCount() <= 0) {
            return ApiResponse.error("采购数量必须大于0");
        }
        Map<Integer, User> lockedUsers =
                lockUsers(CurrentUserContext.userId(), dto.getPurchaserId());
        User requester = lockedUsers.get(CurrentUserContext.userId());
        if (!isCurrentUserStateValid(requester) || !isAdmin(requester.getUserRole())) {
            return ApiResponse.error("当前账号状态不允许创建采购单");
        }
        User purchaser = dto.getPurchaserId() == null ? null : lockedUsers.get(dto.getPurchaserId());
        if (dto.getPurchaserId() != null
                && (!isActiveUser(purchaser)
                || !Objects.equals(purchaser.getUserRole(), UserRole.ACQUISITIONS.code()))) {
            return ApiResponse.error("只能指派采购员处理采购单");
        }
        Book book = bookMapper.findByIdForUpdate(dto.getBookId());
        if (book == null || Boolean.TRUE.equals(book.getIsDeleted())) {
            return ApiResponse.error("图书不存在");
        }
        LocalDateTime now = LocalDateTime.now();
        ProcurementOrder order = ProcurementOrder.builder()
                .bookId(book.getId())
                .bookName(book.getName())
                .isbn(book.getIsbn())
                .category(book.getCategory())
                .requestCount(dto.getRequestCount())
                .status(ORDER_PENDING)
                .requesterId(CurrentUserContext.userId())
                .purchaserId(dto.getPurchaserId())
                .requestNote(cleanPlainText(dto.getRequestNote()))
                .stockApplied(false)
                .createTime(now)
                .updateTime(now)
                .build();
        if (procurementOrderMapper.insert(order) != 1) {
            return ApiResponse.error("采购单创建失败，请重试");
        }
        operationAuditService.record("新增", "采购单",
                orderIdentity(order) + "，创建采购单，申请说明=" + auditText(order.getRequestNote()));
        if (order.getPurchaserId() != null) {
            operationAuditService.record("指派", "采购单",
                    orderIdentity(order) + "，采购员：未指派 -> " + userLabel(order.getPurchaserId()));
        }
        return ApiResponse.success("采购单已创建");
    }

    @Override
    @Transactional
    public ApiResponse<Void> assignPurchaser(ProcurementAssignDto dto) {
        if (dto == null || dto.getOrderId() == null || dto.getUserId() == null) {
            return ApiResponse.error("请选择采购单和采购员");
        }
        Map<Integer, User> lockedUsers =
                lockUsers(CurrentUserContext.userId(), dto.getUserId());
        User actor = lockedUsers.get(CurrentUserContext.userId());
        if (!isCurrentUserStateValid(actor) || !isAdmin(actor.getUserRole())) {
            return ApiResponse.error("当前账号权限已变化，请刷新后重试");
        }
        User purchaser = lockedUsers.get(dto.getUserId());
        if (!isActiveUser(purchaser)
                || !Objects.equals(purchaser.getUserRole(), UserRole.ACQUISITIONS.code())) {
            return ApiResponse.error("请选择有效采购员");
        }
        ProcurementOrder order = requireOrderForUpdate(dto == null ? null : dto.getOrderId());
        if (order == null) {
            return ApiResponse.error("采购单不存在");
        }
        if (isAdmin(CurrentUserContext.roleCode()) && !canCurrentAdminAccess(order)) {
            return ApiResponse.error("只能指派自己创建的采购单");
        }
        if (isFinalStatus(order.getStatus())) {
            return ApiResponse.error("已结束的采购单不能重新指派");
        }
        if (procurementOrderMapper.update(ProcurementOrder.builder()
                .id(order.getId())
                .purchaserId(dto.getUserId())
                .updateTime(LocalDateTime.now())
                .build()) != 1) {
            return stateChanged("采购单状态已变化，请刷新后重试");
        }
        if (!Objects.equals(order.getPurchaserId(), dto.getUserId())) {
            operationAuditService.record("指派", "采购单",
                    orderIdentity(order) + "，采购员：" + userLabel(order.getPurchaserId())
                            + " -> " + userLabel(dto.getUserId()));
        }
        return ApiResponse.success("采购员已指派");
    }

    @Override
    @Transactional
    public ApiResponse<Void> claim(Integer id) {
        User currentUser = lockUser(CurrentUserContext.userId());
        if (!isCurrentUserStateValid(currentUser)
                || !Objects.equals(currentUser.getUserRole(), UserRole.ACQUISITIONS.code())) {
            return ApiResponse.error("只有正常状态的采购员可以认领采购单");
        }
        ProcurementOrder order = requireOrderForUpdate(id);
        if (order == null) {
            return ApiResponse.error("采购单不存在");
        }
        if (!isPurchaser(CurrentUserContext.roleCode())) {
            return ApiResponse.error("只有采购员可以认领采购单");
        }
        Integer currentUserId = CurrentUserContext.userId();
        if (order.getPurchaserId() != null && !Objects.equals(order.getPurchaserId(), currentUserId)) {
            return ApiResponse.error("该采购单已由其他采购员处理");
        }
        if (isFinalStatus(order.getStatus())) {
            return ApiResponse.error("已结束的采购单不能认领");
        }
        ProcurementOrder update = ProcurementOrder.builder()
                .id(order.getId())
                .purchaserId(currentUserId)
                .status(Math.max(order.getStatus(), ORDER_PURCHASING))
                .updateTime(LocalDateTime.now())
                .build();
        if (procurementOrderMapper.update(update) != 1) {
            return stateChanged("采购单状态已变化，请刷新后重试");
        }
        if (!Objects.equals(order.getPurchaserId(), currentUserId)
                || order.getStatus() < ORDER_PURCHASING) {
            operationAuditService.record("认领", "采购单",
                    orderIdentity(order) + "，认领人=" + userLabel(currentUserId)
                            + "，采购状态：" + orderStatusName(order.getStatus())
                            + " -> " + orderStatusName(update.getStatus()));
        }
        return ApiResponse.success("采购单已认领");
    }

    @Override
    @Transactional
    public ApiResponse<Void> updateStatus(ProcurementStatusUpdateDto dto) {
        User currentUser = lockUser(CurrentUserContext.userId());
        if (!isCurrentUserStateValid(currentUser)) {
            return ApiResponse.error("当前账号状态不允许更新采购单");
        }
        ProcurementOrder order = requireOrderForUpdate(dto == null ? null : dto.getId());
        if (order == null) {
            return ApiResponse.error("采购单不存在");
        }
        Integer targetStatus = dto.getStatus();
        if (targetStatus == null || targetStatus < ORDER_PENDING || targetStatus > ORDER_CANCELED) {
            return ApiResponse.error("采购状态不正确");
        }
        Integer roleId = CurrentUserContext.roleCode();
        if (isLogistics(roleId)) {
            return ApiResponse.error("物流员请更新物流进度");
        }
        if (isAdmin(roleId) && !canCurrentAdminAccess(order)) {
            return ApiResponse.error("只能处理自己创建的采购单");
        }
        if (!isAdmin(roleId) && !canCurrentPurchaserHandle(order)) {
            return ApiResponse.error("无权处理该采购单");
        }
        if (isFinalStatus(order.getStatus())) {
            return ApiResponse.error("已结束的采购单不能再更新");
        }
        if (targetStatus == ORDER_WAREHOUSED) {
            return ApiResponse.error("入库状态必须通过物流入库操作完成");
        }
        if (targetStatus == ORDER_SHIPPED || targetStatus == ORDER_ARRIVED) {
            return ApiResponse.error("发货和到货状态必须通过物流进度更新");
        }
        if (!isAllowedOrderTransition(order.getStatus(), targetStatus)) {
            return ApiResponse.error("采购状态流转不合法");
        }

        LocalDateTime now = LocalDateTime.now();
        ProcurementOrder update = ProcurementOrder.builder()
                .id(order.getId())
                .status(targetStatus)
                .purchaseNote(cleanPlainText(dto.getPurchaseNote()))
                .updateTime(now)
                .build();
        if (isPurchaser(roleId) && order.getPurchaserId() == null) {
            update.setPurchaserId(CurrentUserContext.userId());
        }
        fillStatusTime(update, targetStatus, now);
        if (procurementOrderMapper.update(update) != 1) {
            return stateChanged("采购单状态已变化，请刷新后重试");
        }
        if (update.getPurchaserId() != null) {
            operationAuditService.record("认领", "采购单",
                    orderIdentity(order) + "，认领人=" + userLabel(update.getPurchaserId())
                            + "，通过状态更新自动认领");
        }
        if (!Objects.equals(order.getStatus(), targetStatus)) {
            String operation = Objects.equals(targetStatus, ORDER_CANCELED) ? "取消" : "流转";
            operationAuditService.record(operation, "采购单",
                    orderIdentity(order) + "，采购状态：" + orderStatusName(order.getStatus())
                            + " -> " + orderStatusName(targetStatus)
                            + "，采购备注=" + auditText(update.getPurchaseNote()));
        }
        return ApiResponse.success("采购状态已更新");
    }

    @Override
    @Transactional
    public ApiResponse<Void> assignLogistics(ProcurementAssignDto dto) {
        if (dto == null || dto.getOrderId() == null || dto.getUserId() == null) {
            return ApiResponse.error("请选择采购单和物流员");
        }
        Map<Integer, User> lockedUsers =
                lockUsers(CurrentUserContext.userId(), dto.getUserId());
        User actor = lockedUsers.get(CurrentUserContext.userId());
        if (!isCurrentUserStateValid(actor)
                || (!isPurchaser(actor.getUserRole()) && !isSuperAdmin(actor.getUserRole()))) {
            return ApiResponse.error("当前账号权限已变化，请刷新后重试");
        }
        User logisticsUser = lockedUsers.get(dto.getUserId());
        if (!isActiveUser(logisticsUser)
                || !Objects.equals(logisticsUser.getUserRole(), UserRole.LOGISTICS.code())) {
            return ApiResponse.error("请选择有效物流员");
        }
        ProcurementOrder order = requireOrderForUpdate(dto == null ? null : dto.getOrderId());
        if (order == null) {
            return ApiResponse.error("采购单不存在");
        }
        if (isFinalStatus(order.getStatus())) {
            return ApiResponse.error("已结束的采购单不能分配物流");
        }
        if (order.getStatus() < ORDER_PLACED) {
            return ApiResponse.error("采购单下单后才能分配物流");
        }
        if (order.getStatus() >= ORDER_WAREHOUSED) {
            return ApiResponse.error("已入库的采购单不能重新分配物流");
        }
        if (!isSuperAdmin(CurrentUserContext.roleCode()) && !canCurrentPurchaserHandle(order)) {
            return ApiResponse.error("只有负责该单的采购员可以分配物流");
        }
        LocalDateTime now = LocalDateTime.now();
        ProcurementOrder update = ProcurementOrder.builder()
                .id(order.getId())
                .logisticsId(dto.getUserId())
                .updateTime(now)
                .build();
        if (isPurchaser(CurrentUserContext.roleCode()) && order.getPurchaserId() == null) {
            update.setPurchaserId(CurrentUserContext.userId());
        }
        if (procurementOrderMapper.update(update) != 1) {
            return stateChanged("采购单状态已变化，请刷新后重试");
        }

        ProcurementLogistics logistics = procurementLogisticsMapper.getByOrderId(order.getId());
        if (logistics == null) {
            if (procurementLogisticsMapper.insert(ProcurementLogistics.builder()
                    .orderId(order.getId())
                    .logisticsUserId(dto.getUserId())
                    .status(LOGISTICS_PENDING)
                    .createTime(now)
                    .updateTime(now)
                    .build()) != 1) {
                return stateChanged("物流任务创建失败，请刷新后重试");
            }
        } else {
            logistics.setLogisticsUserId(dto.getUserId());
            logistics.setUpdateTime(now);
            if (procurementLogisticsMapper.updateById(logistics) != 1) {
                return stateChanged("物流任务状态已变化，请刷新后重试");
            }
        }
        if (!Objects.equals(order.getLogisticsId(), dto.getUserId())) {
            operationAuditService.record("指派", "物流任务",
                    orderIdentity(order) + "，物流员：" + userLabel(order.getLogisticsId())
                            + " -> " + userLabel(dto.getUserId()));
        }
        return ApiResponse.success("物流员已分配");
    }

    @Override
    @Transactional
    public ApiResponse<Void> updateLogistics(ProcurementLogisticsUpdateDto dto) {
        User currentUser = lockUser(CurrentUserContext.userId());
        if (!isCurrentUserStateValid(currentUser)) {
            return ApiResponse.error("当前账号状态不允许更新物流进度");
        }
        ProcurementOrder order = requireOrderForUpdate(dto == null ? null : dto.getOrderId());
        if (order == null) {
            return ApiResponse.error("采购单不存在");
        }
        Integer roleId = CurrentUserContext.roleCode();
        Integer currentUserId = CurrentUserContext.userId();
        if (!isSuperAdmin(roleId) && !isLogistics(roleId) && !canCurrentPurchaserHandle(order)) {
            return ApiResponse.error("无权更新物流进度");
        }
        if (isLogistics(roleId) && !Objects.equals(order.getLogisticsId(), currentUserId)) {
            return ApiResponse.error("只能更新分配给自己的物流任务");
        }
        if (isFinalStatus(order.getStatus())) {
            return ApiResponse.error("已结束的采购单不能更新物流进度");
        }
        if (order.getStatus() < ORDER_PLACED) {
            return ApiResponse.error("采购单下单后才能更新物流进度");
        }
        Integer targetStatus = dto.getStatus();
        if (targetStatus == null || targetStatus < LOGISTICS_PENDING || targetStatus > LOGISTICS_WAREHOUSED) {
            return ApiResponse.error("物流状态不正确");
        }
        if (order.getLogisticsId() == null) {
            return ApiResponse.error("请先分配物流员");
        }
        ProcurementLogistics logistics = procurementLogisticsMapper.getByOrderId(order.getId());
        if (logistics == null) {
            return ApiResponse.error("物流任务不存在，请重新分配物流员");
        }
        Integer previousLogisticsStatus = logistics.getStatus();
        if (!isAllowedLogisticsTransition(previousLogisticsStatus, targetStatus)) {
            return ApiResponse.error("物流状态流转不合法");
        }
        LocalDateTime now = LocalDateTime.now();
        logistics.setStatus(targetStatus);
        logistics.setTrackingNo(cleanPlainText(dto.getTrackingNo()));
        logistics.setCarrier(cleanPlainText(dto.getCarrier()));
        logistics.setRemark(cleanPlainText(dto.getRemark()));
        logistics.setUpdateTime(now);
        if (procurementLogisticsMapper.updateById(ProcurementLogistics.builder()
                .id(logistics.getId())
                .status(logistics.getStatus())
                .trackingNo(logistics.getTrackingNo())
                .carrier(logistics.getCarrier())
                .remark(logistics.getRemark())
                .updateTime(logistics.getUpdateTime())
                .build()) != 1) {
            return stateChanged("物流任务状态已变化，请刷新后重试");
        }

        ProcurementOrder orderUpdate = ProcurementOrder.builder()
                .id(order.getId())
                .updateTime(now)
                .build();
        Integer mappedOrderStatus = mapLogisticsStatus(targetStatus);
        if (mappedOrderStatus != null && mappedOrderStatus > order.getStatus()) {
            orderUpdate.setStatus(mappedOrderStatus);
            fillStatusTime(orderUpdate, mappedOrderStatus, now);
        }
        if (procurementOrderMapper.update(orderUpdate) != 1) {
            return stateChanged("采购单状态已变化，请刷新后重试");
        }

        boolean stockApplied = false;
        if (targetStatus == LOGISTICS_WAREHOUSED) {
            stockApplied = applyStockOnce(order);
        }
        if (!Objects.equals(previousLogisticsStatus, targetStatus)) {
            String operation = Objects.equals(targetStatus, LOGISTICS_WAREHOUSED) ? "入库" : "流转";
            String mappedStatusDetail = mappedOrderStatus != null && mappedOrderStatus > order.getStatus()
                    ? "，采购状态：" + orderStatusName(order.getStatus())
                    + " -> " + orderStatusName(mappedOrderStatus)
                    : "";
            operationAuditService.record(operation, "物流任务",
                    orderIdentity(order) + "，物流状态："
                            + logisticsStatusName(previousLogisticsStatus)
                            + " -> " + logisticsStatusName(targetStatus)
                            + mappedStatusDetail
                            + "，物流员=" + userLabel(order.getLogisticsId())
                            + "，承运方=" + auditText(logistics.getCarrier())
                            + "，运单号=" + auditText(logistics.getTrackingNo())
                            + "，备注=" + auditText(logistics.getRemark()));
        }
        if (stockApplied) {
            operationAuditService.record("库存补充", "图书库存",
                    orderIdentity(order) + "，库存增加=" + order.getRequestCount()
                            + "，stockApplied=false -> true");
            notifyReservationsAfterCommit(order.getBookId());
        }
        return ApiResponse.success("物流进度已更新");
    }

    @Override
    public ApiResponse<List<ProcurementOrderView>> query(ProcurementOrderPageQuery dto) {
        if (dto == null) {
            dto = new ProcurementOrderPageQuery();
        }
        Integer roleId = CurrentUserContext.roleCode();
        Integer userId = CurrentUserContext.userId();
        if (isLogistics(roleId)) {
            dto.setLogisticsId(CurrentUserContext.userId());
        } else if (isPurchaser(roleId)) {
            dto.setPurchaserId(userId);
            dto.setIncludeUnassignedForPurchaser(true);
        } else if (isAdmin(roleId) && !canCurrentAdminAccessAllOrders()) {
            dto.setRequesterId(userId);
        }
        List<ProcurementOrderView> list = procurementOrderMapper.query(dto);
        Map<Integer, Integer> unreadCounts = unreadCountsByOrder(list, userId);
        for (ProcurementOrderView item : list) {
            item.setRequestNote(cleanPlainText(item.getRequestNote()));
            item.setPurchaseNote(cleanPlainText(item.getPurchaseNote()));
            item.setTrackingNo(cleanPlainText(item.getTrackingNo()));
            item.setCarrier(cleanPlainText(item.getCarrier()));
            item.setLogisticsRemark(cleanPlainText(item.getLogisticsRemark()));
            item.setUnreadCount(unreadCounts.getOrDefault(item.getId(), 0));
        }
        Integer total = procurementOrderMapper.queryCount(dto);
        return PageResponse.success(list, total);
    }

    private Map<Integer, Integer> unreadCountsByOrder(List<ProcurementOrderView> orders, Integer userId) {
        if (orders == null || orders.isEmpty() || userId == null) {
            return Collections.emptyMap();
        }
        List<Integer> orderIds = orders.stream()
                .map(ProcurementOrderView::getId)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
        if (orderIds.isEmpty()) {
            return Collections.emptyMap();
        }
        List<OrderUnreadSummary> summaries =
                procurementMessageMapper.countUnreadByOrderIds(userId, orderIds);
        if (summaries == null || summaries.isEmpty()) {
            return Collections.emptyMap();
        }
        return summaries.stream().collect(Collectors.toMap(
                OrderUnreadSummary::getOrderId,
                summary -> summary.getUnreadCount() == null ? 0 : summary.getUnreadCount(),
                Integer::sum,
                LinkedHashMap::new));
    }

    @Override
    public ApiResponse<Map<String, Object>> lowStock(Integer threshold) {
        int actualThreshold = threshold == null || threshold < 0 ? 3 : threshold;
        List<Book> books = bookMapper.queryLowStock(actualThreshold);
        Map<String, Object> result = new HashMap<>();
        result.put("books", books);
        result.put("total", books.size());
        result.put("threshold", actualThreshold);
        return ApiResponse.success(result);
    }

    @Override
    @Transactional
    public ApiResponse<Void> sendMessage(ProcurementMessageDto dto) {
        if (dto == null || dto.getOrderId() == null || dto.getReceiverId() == null) {
            return ApiResponse.error("采购单、接收人和消息内容不能为空");
        }
        Map<Integer, User> lockedUsers =
                lockUsers(CurrentUserContext.userId(), dto.getReceiverId());
        User sender = lockedUsers.get(CurrentUserContext.userId());
        User receiver = lockedUsers.get(dto.getReceiverId());
        if (!isCurrentUserStateValid(sender)) {
            return ApiResponse.error("当前账号状态不允许发送协作消息");
        }
        if (!isActiveUser(receiver)) {
            return ApiResponse.error("接收人不存在或账号不可用");
        }
        ProcurementOrder order = requireOrderForUpdate(dto == null ? null : dto.getOrderId());
        if (order == null) {
            return ApiResponse.error("采购单不存在");
        }
        if (ContentSanitizer.exceedsLength(
                dto.getContent(), ContentSanitizer.PROCUREMENT_MESSAGE_MAX_LENGTH)) {
            return ApiResponse.error("消息内容不能超过1000个字符");
        }
        String cleanContent = ContentSanitizer.plainText(dto.getContent());
        if (cleanContent == null || cleanContent.isEmpty()) {
            return ApiResponse.error("接收人和消息内容不能为空");
        }
        String channelError = validateMessageChannel(order, dto.getChannelType(), sender.getId(),
                sender.getUserRole(), receiver.getId(), receiver.getUserRole());
        if (channelError != null) {
            return ApiResponse.error(channelError);
        }
        ProcurementMessage message = ProcurementMessage.builder()
                .orderId(order.getId())
                .channelType(dto.getChannelType())
                .senderId(sender.getId())
                .receiverId(receiver.getId())
                .content(cleanContent)
                .readStatus(false)
                .createTime(LocalDateTime.now())
                .build();
        if (procurementMessageMapper.insert(message) != 1) {
            return stateChanged("消息发送失败，请重试");
        }
        return ApiResponse.success("消息已发送");
    }

    @Override
    public ApiResponse<List<ProcurementMessageView>> queryMessages(ProcurementMessagePageQuery dto) {
        ProcurementOrder order = requireOrder(dto == null ? null : dto.getOrderId());
        if (order == null) {
            return ApiResponse.error("采购单不存在");
        }
        String error = validateChannelView(order, dto.getChannelType());
        if (error != null) {
            return ApiResponse.error(error);
        }
        List<ProcurementMessageView> list = procurementMessageMapper.query(dto);
        for (ProcurementMessageView message : list) {
            message.setContent(ContentSanitizer.plainText(message.getContent()));
        }
        Integer total = procurementMessageMapper.queryCount(dto);
        return PageResponse.success(list, total);
    }

    @Override
    @Transactional
    public ApiResponse<Void> markRead(ProcurementMessageReadDto dto) {
        List<Integer> messageIds = IdListUtils.normalize(dto == null ? null : dto.getMessageIds());
        if (messageIds.isEmpty() || messageIds.size() > 100) {
            return ApiResponse.error("请选择不超过100条已展示消息");
        }
        ProcurementOrder order = requireOrder(dto.getOrderId());
        if (order == null) {
            return ApiResponse.error("采购单不存在");
        }
        String error = validateChannelView(order, dto.getChannelType());
        if (error != null) {
            return ApiResponse.error(error);
        }
        procurementMessageMapper.markRead(
                CurrentUserContext.userId(),
                dto.getOrderId(),
                dto.getChannelType(),
                messageIds,
                LocalDateTime.now());
        return ApiResponse.success("消息已读");
    }

    @Override
    public ApiResponse<Map<String, Object>> unreadCount(Integer orderId) {
        Map<String, Object> result = new HashMap<>();
        Integer userId = CurrentUserContext.userId();
        result.put("total", procurementMessageMapper.countUnread(userId, orderId, null));
        result.put("adminPurchaser", procurementMessageMapper.countUnread(userId, orderId, CHANNEL_ADMIN_PURCHASER));
        result.put("purchaserLogistics", procurementMessageMapper.countUnread(userId, orderId, CHANNEL_PURCHASER_LOGISTICS));
        return ApiResponse.success(result);
    }

    private ProcurementOrder requireOrder(Integer id) {
        return id == null ? null : procurementOrderMapper.getById(id);
    }

    private ProcurementOrder requireOrderForUpdate(Integer id) {
        return id == null ? null : procurementOrderMapper.findByIdForUpdate(id);
    }

    private <T> ApiResponse<T> stateChanged(String message) {
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
        }
        return ApiResponse.error(message);
    }

    private boolean canCurrentPurchaserHandle(ProcurementOrder order) {
        Integer currentUserId = CurrentUserContext.userId();
        Integer roleId = CurrentUserContext.roleCode();
        return isPurchaser(roleId)
                && (order.getPurchaserId() == null || Objects.equals(order.getPurchaserId(), currentUserId));
    }

    private boolean applyStockOnce(ProcurementOrder order) {
        if (Boolean.TRUE.equals(order.getStockApplied())) {
            return false;
        }
        int marked = procurementOrderMapper.markStockApplied(order.getId());
        if (marked == 0) {
            return false;
        }
        int updated = bookMapper.increaseStock(order.getBookId(), order.getRequestCount());
        if (updated == 0) {
            throw new IllegalStateException("采购入库失败，图书不存在或数量无效");
        }
        recommendationSourceVersionService.invalidateGlobalAfterCommit();
        return true;
    }

    private Integer mapLogisticsStatus(Integer status) {
        if (Objects.equals(status, LOGISTICS_TRANSIT)) {
            return ORDER_SHIPPED;
        }
        if (Objects.equals(status, LOGISTICS_ARRIVED)) {
            return ORDER_ARRIVED;
        }
        if (Objects.equals(status, LOGISTICS_WAREHOUSED)) {
            return ORDER_WAREHOUSED;
        }
        return null;
    }

    private void fillStatusTime(ProcurementOrder order, Integer status, LocalDateTime now) {
        if (Objects.equals(status, ORDER_PLACED)) {
            order.setOrderTime(now);
        } else if (Objects.equals(status, ORDER_SHIPPED)) {
            order.setShippedTime(now);
        } else if (Objects.equals(status, ORDER_ARRIVED)) {
            order.setArrivalTime(now);
        } else if (Objects.equals(status, ORDER_COMPLETED)) {
            order.setCompletedTime(now);
        }
    }

    private String validateMessageChannel(ProcurementOrder order,
                                          Integer channelType,
                                          Integer senderId,
                                          Integer senderRole,
                                          Integer receiverId,
                                          Integer receiverRole) {
        if (Objects.equals(senderId, receiverId)) {
            return "不能给自己发送协作消息";
        }
        if (Objects.equals(channelType, CHANNEL_ADMIN_PURCHASER)) {
            boolean adminToPurchaser = isAdmin(senderRole) && isPurchaser(receiverRole);
            boolean purchaserToAdmin = isPurchaser(senderRole) && isAdmin(receiverRole);
            if (!adminToPurchaser && !purchaserToAdmin) {
                return "管理员与采购员通道只允许管理员和采购员沟通";
            }
            if (order.getPurchaserId() == null) {
                return "请先指派或认领采购员";
            }
            Integer purchaserId = isPurchaser(senderRole) ? senderId : receiverId;
            if (!Objects.equals(order.getPurchaserId(), purchaserId)) {
                return "只能与该采购单的负责采购员沟通";
            }
            Integer adminId = isAdmin(senderRole) ? senderId : receiverId;
            Integer adminRole = isAdmin(senderRole) ? senderRole : receiverRole;
            if (!canAdminAccessOrder(adminId, adminRole, order)) {
                return "普通管理员只能参与自己创建采购单的沟通";
            }
            return null;
        }
        if (Objects.equals(channelType, CHANNEL_PURCHASER_LOGISTICS)) {
            boolean purchaserToLogistics = isPurchaser(senderRole) && isLogistics(receiverRole);
            boolean logisticsToPurchaser = isLogistics(senderRole) && isPurchaser(receiverRole);
            if (!purchaserToLogistics && !logisticsToPurchaser) {
                return "采购员与物流员通道只允许采购员和物流员沟通";
            }
            if (order.getPurchaserId() == null || order.getLogisticsId() == null) {
                return "请先确认采购员和物流员";
            }
            Integer purchaserId = isPurchaser(senderRole) ? senderId : receiverId;
            Integer logisticsId = isLogistics(senderRole) ? senderId : receiverId;
            if (!Objects.equals(order.getPurchaserId(), purchaserId)
                    || !Objects.equals(order.getLogisticsId(), logisticsId)) {
                return "只能在当前采购单的采购员和物流员之间沟通";
            }
            return null;
        }
        return "消息通道不正确";
    }

    private String validateChannelView(ProcurementOrder order, Integer channelType) {
        Integer roleId = CurrentUserContext.roleCode();
        Integer userId = CurrentUserContext.userId();
        if (Objects.equals(channelType, CHANNEL_ADMIN_PURCHASER)) {
            if (isSuperAdmin(roleId)) {
                return null;
            }
            if (canAdminAccessOrder(userId, roleId, order)) {
                return null;
            }
            if (isPurchaser(roleId) && Objects.equals(order.getPurchaserId(), userId)) {
                return null;
            }
            return "无权查看管理员与采购员消息";
        }
        if (Objects.equals(channelType, CHANNEL_PURCHASER_LOGISTICS)) {
            if (isSuperAdmin(roleId)) {
                return null;
            }
            if (isPurchaser(roleId) && Objects.equals(order.getPurchaserId(), userId)) {
                return null;
            }
            if (isLogistics(roleId) && Objects.equals(order.getLogisticsId(), userId)) {
                return null;
            }
            return "无权查看采购员与物流员消息";
        }
        return "消息通道不正确";
    }

    private User lockUser(Integer userId) {
        return userId == null ? null : userMapper.findByIdForUpdate(userId);
    }

    private Map<Integer, User> lockUsers(Integer... userIds) {
        List<Integer> ids = new ArrayList<>();
        if (userIds != null) {
            for (Integer userId : userIds) {
                if (userId != null && !ids.contains(userId)) {
                    ids.add(userId);
                }
            }
        }
        Collections.sort(ids);
        Map<Integer, User> users = new LinkedHashMap<>();
        for (Integer userId : ids) {
            users.put(userId, userMapper.findByIdForUpdate(userId));
        }
        return users;
    }

    private boolean isActiveUser(User user) {
        return user != null
                && Objects.equals(user.getAccountStatus(), AccountStatus.NORMAL.code())
                && !Boolean.TRUE.equals(user.getIsLogin());
    }

    private boolean isCurrentUserStateValid(User user) {
        return isActiveUser(user)
                && Objects.equals(user.getUserRole(), CurrentUserContext.roleCode());
    }

    private boolean canCurrentAdminAccess(ProcurementOrder order) {
        Integer roleId = CurrentUserContext.roleCode();
        if (canCurrentAdminAccessAllOrders()) {
            return true;
        }
        return Objects.equals(roleId, UserRole.ADMIN.code())
                && Objects.equals(order.getRequesterId(), CurrentUserContext.userId());
    }

    private boolean canCurrentAdminAccessAllOrders() {
        Integer roleId = CurrentUserContext.roleCode();
        return isSuperAdmin(roleId) || isCoordinatorAdmin(CurrentUserContext.userId(), roleId);
    }

    private boolean canAdminAccessOrder(Integer adminId, Integer adminRole, ProcurementOrder order) {
        if (isSuperAdmin(adminRole) || isCoordinatorAdmin(adminId, adminRole)) {
            return true;
        }
        return Objects.equals(adminRole, UserRole.ADMIN.code())
                && Objects.equals(order.getRequesterId(), adminId);
    }

    private boolean isCoordinatorAdmin(Integer userId, Integer roleId) {
        if (!Objects.equals(roleId, UserRole.ADMIN.code())) {
            return false;
        }
        User user = userMapper.getById(userId);
        return user != null && Boolean.TRUE.equals(user.getIsCoordinatorAdmin());
    }

    private boolean isAdmin(Integer roleId) {
        return Objects.equals(roleId, UserRole.SUPER_ADMIN.code())
                || Objects.equals(roleId, UserRole.ADMIN.code());
    }

    private boolean isSuperAdmin(Integer roleId) {
        return Objects.equals(roleId, UserRole.SUPER_ADMIN.code());
    }

    private boolean isPurchaser(Integer roleId) {
        return Objects.equals(roleId, UserRole.ACQUISITIONS.code());
    }

    private boolean isLogistics(Integer roleId) {
        return Objects.equals(roleId, UserRole.LOGISTICS.code());
    }

    private boolean isFinalStatus(Integer status) {
        return Objects.equals(status, ORDER_COMPLETED) || Objects.equals(status, ORDER_CANCELED);
    }

    private boolean isAllowedOrderTransition(Integer current, Integer target) {
        if (Objects.equals(current, target)) {
            return true;
        }
        if (Objects.equals(target, ORDER_CANCELED)) {
            return current != null && current < ORDER_WAREHOUSED;
        }
        if (Objects.equals(target, ORDER_COMPLETED)) {
            return Objects.equals(current, ORDER_WAREHOUSED);
        }
        return current != null && target != null
                && target == current + 1
                && target <= ORDER_PLACED;
    }

    private boolean isAllowedLogisticsTransition(Integer current, Integer target) {
        return Objects.equals(current, target)
                || (current != null && target != null && target == current + 1);
    }

    private String orderIdentity(ProcurementOrder order) {
        return "采购单ID=" + order.getId()
                + "，图书ID=" + order.getBookId()
                + "，书名=" + order.getBookName()
                + "，采购数量=" + order.getRequestCount();
    }

    private String userLabel(Integer userId) {
        if (userId == null) {
            return "未指派";
        }
        User user = userMapper.getById(userId);
        return user == null ? "用户#" + userId : user.getUserName() + "(ID=" + userId + ")";
    }

    private String orderStatusName(Integer status) {
        if (Objects.equals(status, ORDER_PENDING)) return "待采购";
        if (Objects.equals(status, ORDER_PURCHASING)) return "采购中";
        if (Objects.equals(status, ORDER_PLACED)) return "已下单";
        if (Objects.equals(status, ORDER_SHIPPED)) return "已发货";
        if (Objects.equals(status, ORDER_ARRIVED)) return "已到货";
        if (Objects.equals(status, ORDER_WAREHOUSED)) return "已入库";
        if (Objects.equals(status, ORDER_COMPLETED)) return "已完成";
        if (Objects.equals(status, ORDER_CANCELED)) return "已取消";
        return "未知状态(" + status + ")";
    }

    private String logisticsStatusName(Integer status) {
        if (Objects.equals(status, LOGISTICS_PENDING)) return "待接收";
        if (Objects.equals(status, LOGISTICS_TRANSIT)) return "运输中";
        if (Objects.equals(status, LOGISTICS_ARRIVED)) return "已到馆";
        if (Objects.equals(status, LOGISTICS_WAREHOUSED)) return "已入库";
        return "未知状态(" + status + ")";
    }

    private String auditText(String value) {
        String cleanValue = trimToNull(value);
        if (cleanValue == null) {
            return "无";
        }
        return cleanValue.length() <= 200 ? cleanValue : cleanValue.substring(0, 200) + "...";
    }

    private String trimToNull(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        return value.trim();
    }

    private String cleanPlainText(String value) {
        return trimToNull(ContentSanitizer.plainText(value));
    }

    private void notifyReservationsAfterCommit(Integer bookId) {
        Runnable notification = () -> {
            try {
                reservationWorkflowService.onBookReturned(bookId);
            } catch (Exception e) {
                log.warn("采购入库后的预约通知失败，等待定时对账: bookId={}, error={}",
                        bookId, e.getMessage());
            }
        };
        TransactionCallbacks.afterCommit(notification);
    }
}
