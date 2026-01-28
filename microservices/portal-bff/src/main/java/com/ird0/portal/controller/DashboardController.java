package com.ird0.portal.controller;

import com.ird0.portal.dto.response.DashboardDTO;
import com.ird0.portal.service.DashboardService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("${portal.api.base-path:/api/portal/v1}")
@RequiredArgsConstructor
@Tag(name = "Dashboard", description = "Dashboard KPIs and overview")
public class DashboardController {

  private final DashboardService dashboardService;

  @Operation(summary = "Get dashboard data", operationId = "getDashboard")
  @ApiResponse(responseCode = "200", description = "Dashboard KPIs and statistics")
  @GetMapping("/dashboard")
  @PreAuthorize("hasRole('claims-viewer')")
  public ResponseEntity<DashboardDTO> getDashboard() {
    log.debug("Getting dashboard data");
    DashboardDTO dashboard = dashboardService.getDashboard();
    return ResponseEntity.ok(dashboard);
  }
}
