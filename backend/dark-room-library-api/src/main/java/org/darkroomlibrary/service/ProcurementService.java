package org.darkroomlibrary.service;

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

import java.util.List;
import java.util.Map;

/**
 * 采购协作服务
 */
public interface ProcurementService {

    ApiResponse<Void> save(ProcurementOrderCreateDto dto);

    ApiResponse<Void> assignPurchaser(ProcurementAssignDto dto);

    ApiResponse<Void> claim(Integer id);

    ApiResponse<Void> updateStatus(ProcurementStatusUpdateDto dto);

    ApiResponse<Void> assignLogistics(ProcurementAssignDto dto);

    ApiResponse<Void> updateLogistics(ProcurementLogisticsUpdateDto dto);

    ApiResponse<List<ProcurementOrderView>> query(ProcurementOrderPageQuery dto);

    ApiResponse<Map<String, Object>> lowStock(Integer threshold);

    ApiResponse<Void> sendMessage(ProcurementMessageDto dto);

    ApiResponse<List<ProcurementMessageView>> queryMessages(ProcurementMessagePageQuery dto);

    ApiResponse<Void> markRead(Integer orderId, Integer channelType);

    ApiResponse<Map<String, Object>> unreadCount(Integer orderId);
}
