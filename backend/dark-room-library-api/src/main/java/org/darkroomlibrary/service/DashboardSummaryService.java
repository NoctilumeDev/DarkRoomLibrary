package org.darkroomlibrary.service;

import org.darkroomlibrary.web.response.ApiResponse;
import org.darkroomlibrary.web.view.MetricPoint;

import java.util.List;

public interface DashboardSummaryService {

    ApiResponse<List<MetricPoint>> staticControls();

}
