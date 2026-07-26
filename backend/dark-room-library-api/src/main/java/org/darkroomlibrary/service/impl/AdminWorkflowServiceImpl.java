package org.darkroomlibrary.service.impl;

import org.darkroomlibrary.web.response.ApiResponse;
import org.darkroomlibrary.service.AdminWorkflowService;
import org.darkroomlibrary.mapper.AdminWorkflowMapper;
import org.springframework.stereotype.Service;

import jakarta.annotation.Resource;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class AdminWorkflowServiceImpl implements AdminWorkflowService {

    @Resource
    private AdminWorkflowMapper adminWorkflowMapper;

    @Override
    public ApiResponse<Map<String, Object>> auditStatus() {
        Map<String, Object> result = new LinkedHashMap<>();
        List<Map<String, Object>> cards = new ArrayList<>();
        List<Map<String, Object>> groups = new ArrayList<>();

        Map<String, Object> counts = adminWorkflowMapper.countWorkflowStatuses();
        long messagePending = number(counts, "messagePending");
        long messageReplied = number(counts, "messageReplied");
        long activeBorrow = number(counts, "activeBorrow");
        long overdueBorrow = number(counts, "overdueBorrow");
        long notifiedReservation = number(counts, "notifiedReservation");
        long failedNotification = number(counts, "failedNotification");
        long pendingReviewReport = number(counts, "pendingReviewReport");
        long pendingProcurement = number(counts, "pendingProcurement");
        long activeProcurement = number(counts, "activeProcurement");
        long inTransitLogistics = number(counts, "inTransitLogistics");
        long disabledUsers = number(counts, "disabledUsers");
        long mutedUsers = number(counts, "mutedUsers");

        cards.add(card("待回复留言", messagePending, "留言管理", "warning"));
        cards.add(card("待审举报", pendingReviewReport, "内容审核", "warning"));
        cards.add(card("待采购", pendingProcurement, "采购协作", "warning"));
        cards.add(card("物流在途", inTransitLogistics, "物流进度", "primary"));
        cards.add(card("逾期借阅", overdueBorrow, "流通审核", "danger"));
        cards.add(card("已通知预约", notifiedReservation, "预约履约", "primary"));
        cards.add(card("失败通知", failedNotification, "补偿任务", "danger"));

        groups.add(group("留言处理状态", "读者留言是否已经由管理员回复。",
                item("待回复", messagePending, "warning"),
                item("已回复", messageReplied, "success")));
        groups.add(group("用户状态审核", "关注账号可登录状态和发言状态。",
                item("正常账号", number(counts, "activeUsers"), "success"),
                item("冻结账号", disabledUsers, "danger"),
                item("禁言账号", mutedUsers, "warning")));
        groups.add(group("借阅状态跟踪", "馆员需要优先处理逾期和在借记录。",
                item("借阅中", activeBorrow, "warning"),
                item("已归还", number(counts, "returnedBorrow"), "success"),
                item("逾期未还", overdueBorrow, "danger")));
        groups.add(group("预约状态流转", "预约队列从等待、通知到履约或释放。",
                item("等待中", number(counts, "waitingReservation"), "info"),
                item("已通知", notifiedReservation, "primary"),
                item("已借阅", number(counts, "borrowedReservation"), "success"),
                item("已取消", number(counts, "canceledReservation"), "info"),
                item("已过期", number(counts, "expiredReservation"), "warning")));
        groups.add(group("通知补偿状态", "邮件发送失败时进入补偿任务，避免阻塞主流程。",
                item("待发送", number(counts, "pendingNotification"), "warning"),
                item("已发送", number(counts, "sentNotification"), "success"),
                item("失败重试", failedNotification, "danger")));
        groups.add(group("内容互动状态", "书评、点赞与回复形成读者互动闭环。",
                item("书评数", number(counts, "reviewCount"), "primary"),
                item("点赞数", number(counts, "reviewLikeCount"), "success"),
                item("回复数", number(counts, "reviewReplyCount"), "info"),
                item("待审举报", pendingReviewReport, "warning")));
        groups.add(group("采购物流状态", "管理员创建采购需求，采购员处理采购，物流员同步入库进度。",
                item("待采购", pendingProcurement, "warning"),
                item("采购中", activeProcurement, "primary"),
                item("已入库", number(counts, "warehousedProcurement"), "success"),
                item("已完成", number(counts, "completedProcurement"), "success")));

        result.put("cards", cards);
        result.put("groups", groups);
        return ApiResponse.success(result);
    }

    @Override
    public ApiResponse<Map<String, Object>> backendFlow() {
        Map<String, Object> result = new LinkedHashMap<>();
        List<Map<String, Object>> stages = new ArrayList<>();

        stages.add(stage("接入校验", "Controller",
                "参数校验、白名单、角色保护和登录数学验证码先拦截明显无效请求。",
                "账号登录", "邮箱验证码", "角色保护"));
        stages.add(stage("业务编排", "Service",
                "核心业务规则集中在 Service 层，借书、还书、续借、预约都在事务内完成。",
                "借阅配额", "预约队列", "罚款计算"));
        stages.add(stage("数据一致性", "Mapper / MySQL",
                "库存扣减、还书更新和预约履约使用数据库条件更新，避免库存负数和重复处理。",
                "条件扣库存", "状态流转", "唯一约束"));
        stages.add(stage("事件与通知", "Domain Event",
                "还书后发布预约通知事件，邮件失败时保留补偿任务，不影响主交易。",
                "BookReturnedEvent", "到期提醒", "预约到货"));
        stages.add(stage("可降级中间件", "Redis / RabbitMQ",
                "Redis 和 MQ 只是增强能力，异常时走内存、同步写库或补偿表。",
                "缓存降级", "日志同步", "通知补偿"));
        stages.add(stage("后台审核", "Admin",
                "后台集中查看用户、留言、书评举报、借阅、预约和通知补偿状态，优先处理异常项。",
                "冻结用户", "回复留言", "书评举报", "处理逾期"));
        stages.add(stage("采购物流闭环", "Procurement",
                "管理员创建采购需求，采购员推进采购并对接物流，物流员入库后自动补充馆藏库存。",
                "采购单", "协作消息", "物流入库", "库存补充"));

        result.put("stages", stages);
        result.put("status", auditStatus().getData());
        return ApiResponse.success(result);
    }

    private long number(Map<String, Object> values, String key) {
        Object value = values.get(key);
        if (value == null) {
            value = values.entrySet().stream()
                    .filter(entry -> entry.getKey().equalsIgnoreCase(key))
                    .map(Map.Entry::getValue)
                    .findFirst()
                    .orElse(null);
        }
        return value instanceof Number ? ((Number) value).longValue() : 0L;
    }

    private Map<String, Object> card(String label, long value, String module, String type) {
        Map<String, Object> card = new LinkedHashMap<>();
        card.put("label", label);
        card.put("value", value);
        card.put("module", module);
        card.put("type", type);
        return card;
    }

    @SafeVarargs
    private final Map<String, Object> group(String title, String description, Map<String, Object>... items) {
        Map<String, Object> group = new LinkedHashMap<>();
        group.put("title", title);
        group.put("description", description);
        group.put("items", List.of(items));
        return group;
    }

    private Map<String, Object> item(String label, long value, String type) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("label", label);
        item.put("value", value);
        item.put("type", type);
        return item;
    }

    private Map<String, Object> stage(String title, String layer, String description, String... points) {
        Map<String, Object> stage = new LinkedHashMap<>();
        stage.put("title", title);
        stage.put("layer", layer);
        stage.put("description", description);
        stage.put("points", List.of(points));
        return stage;
    }
}
