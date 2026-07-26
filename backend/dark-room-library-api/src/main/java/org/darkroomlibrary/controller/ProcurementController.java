package org.darkroomlibrary.controller;

import org.darkroomlibrary.aop.NormalizePageQuery;
import org.darkroomlibrary.aop.RequireRole;
import org.darkroomlibrary.domain.type.UserRole;
import org.darkroomlibrary.aop.ManualAudit;
import org.darkroomlibrary.web.response.ApiResponse;
import org.darkroomlibrary.web.dto.query.ProcurementMessagePageQuery;
import org.darkroomlibrary.web.dto.query.ProcurementOrderPageQuery;
import org.darkroomlibrary.web.dto.command.ProcurementAssignDto;
import org.darkroomlibrary.web.dto.command.ProcurementLogisticsUpdateDto;
import org.darkroomlibrary.web.dto.command.ProcurementMessageDto;
import org.darkroomlibrary.web.dto.command.ProcurementOrderCreateDto;
import org.darkroomlibrary.web.dto.command.ProcurementStatusUpdateDto;
import org.darkroomlibrary.web.view.ProcurementMessageView;
import org.darkroomlibrary.web.view.ProcurementOrderView;
import org.darkroomlibrary.service.ProcurementService;
import org.springframework.web.bind.annotation.*;

import jakarta.annotation.Resource;
import java.util.List;
import java.util.Map;
import jakarta.validation.Valid;

/**
 * 采购与物流协作控制器
 */
@RestController
@RequestMapping("/procurement")
public class ProcurementController {

    @Resource
    private ProcurementService procurementService;

    @RequireRole({UserRole.ADMIN, UserRole.SUPER_ADMIN})
    @ManualAudit
    @PostMapping("/save")
    public ApiResponse<Void> save(@Valid @RequestBody ProcurementOrderCreateDto dto) {
        return procurementService.save(dto);
    }

    @RequireRole({UserRole.ADMIN, UserRole.SUPER_ADMIN})
    @ManualAudit
    @PutMapping("/assignPurchaser")
    public ApiResponse<Void> assignPurchaser(@Valid @RequestBody ProcurementAssignDto dto) {
        return procurementService.assignPurchaser(dto);
    }

    @RequireRole(UserRole.ACQUISITIONS)
    @ManualAudit
    @PutMapping("/claim/{id}")
    public ApiResponse<Void> claim(@PathVariable Integer id) {
        return procurementService.claim(id);
    }

    @RequireRole({UserRole.ADMIN, UserRole.SUPER_ADMIN, UserRole.ACQUISITIONS})
    @ManualAudit
    @PutMapping("/updateStatus")
    public ApiResponse<Void> updateStatus(@Valid @RequestBody ProcurementStatusUpdateDto dto) {
        return procurementService.updateStatus(dto);
    }

    @RequireRole({UserRole.ACQUISITIONS, UserRole.SUPER_ADMIN})
    @ManualAudit
    @PutMapping("/assignLogistics")
    public ApiResponse<Void> assignLogistics(@Valid @RequestBody ProcurementAssignDto dto) {
        return procurementService.assignLogistics(dto);
    }

    @RequireRole({UserRole.ACQUISITIONS, UserRole.LOGISTICS, UserRole.SUPER_ADMIN})
    @ManualAudit
    @PutMapping("/updateLogistics")
    public ApiResponse<Void> updateLogistics(@Valid @RequestBody ProcurementLogisticsUpdateDto dto) {
        return procurementService.updateLogistics(dto);
    }

    @NormalizePageQuery
    @RequireRole({UserRole.ADMIN, UserRole.SUPER_ADMIN, UserRole.ACQUISITIONS, UserRole.LOGISTICS})
    @PostMapping("/query")
    public ApiResponse<List<ProcurementOrderView>> query(@RequestBody ProcurementOrderPageQuery dto) {
        return procurementService.query(dto);
    }

    @RequireRole({UserRole.ADMIN, UserRole.SUPER_ADMIN, UserRole.ACQUISITIONS})
    @GetMapping("/lowStock")
    public ApiResponse<Map<String, Object>> lowStock(@RequestParam(defaultValue = "3") Integer threshold) {
        return procurementService.lowStock(threshold);
    }

    @RequireRole({UserRole.ADMIN, UserRole.SUPER_ADMIN, UserRole.ACQUISITIONS, UserRole.LOGISTICS})
    @PostMapping("/message/send")
    public ApiResponse<Void> sendMessage(@Valid @RequestBody ProcurementMessageDto dto) {
        return procurementService.sendMessage(dto);
    }

    @NormalizePageQuery
    @RequireRole({UserRole.ADMIN, UserRole.SUPER_ADMIN, UserRole.ACQUISITIONS, UserRole.LOGISTICS})
    @PostMapping("/message/query")
    public ApiResponse<List<ProcurementMessageView>> queryMessages(@RequestBody ProcurementMessagePageQuery dto) {
        return procurementService.queryMessages(dto);
    }

    @RequireRole({UserRole.ADMIN, UserRole.SUPER_ADMIN, UserRole.ACQUISITIONS, UserRole.LOGISTICS})
    @PutMapping("/message/read")
    public ApiResponse<Void> markRead(@RequestBody ProcurementMessagePageQuery dto) {
        return procurementService.markRead(dto.getOrderId(), dto.getChannelType());
    }

    @RequireRole({UserRole.ADMIN, UserRole.SUPER_ADMIN, UserRole.ACQUISITIONS, UserRole.LOGISTICS})
    @GetMapping("/message/unread")
    public ApiResponse<Map<String, Object>> unreadCount(@RequestParam(required = false) Integer orderId) {
        return procurementService.unreadCount(orderId);
    }
}
