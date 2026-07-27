package org.darkroomlibrary.controller;

import org.darkroomlibrary.aop.RequireRole;
import org.darkroomlibrary.domain.type.UserRole;
import org.darkroomlibrary.service.SystemHealthService;
import org.darkroomlibrary.service.SystemHealthService.HealthReport;
import org.darkroomlibrary.web.response.ApiResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Exposes minimal public probes and protected operational details.
 */
@RestController
@RequestMapping("/health")
public class SystemHealthController {

    private final SystemHealthService healthService;

    public SystemHealthController(SystemHealthService healthService) {
        this.healthService = healthService;
    }

    @GetMapping("/live")
    public Map<String, Object> live() {
        return Map.of(
                "status", "UP",
                "checkedAt", Instant.now()
        );
    }

    @GetMapping("/ready")
    public ResponseEntity<Map<String, Object>> ready() {
        HealthReport report = healthService.checkReadiness();
        Map<String, Object> body = Map.of(
                "status", report.status(),
                "checkedAt", report.checkedAt()
        );
        return ResponseEntity
                .status(report.acceptsTraffic() ? HttpStatus.OK : HttpStatus.SERVICE_UNAVAILABLE)
                .body(body);
    }

    @RequireRole(UserRole.SUPER_ADMIN)
    @GetMapping("/details")
    public ApiResponse<Map<String, Object>> details() {
        HealthReport report = healthService.checkReadiness();
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("status", report.status());
        details.put("checkedAt", report.checkedAt());
        details.put("components", report.components());
        return ApiResponse.success(details);
    }
}
