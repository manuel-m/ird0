package com.ird0.portal.controller;

import com.ird0.portal.dto.response.DashboardDTO;
import com.ird0.portal.service.DashboardService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("${portal.api.base-path:/api/portal/v1}")
@RequiredArgsConstructor
public class DashboardController {

  private final DashboardService dashboardService;

  @GetMapping("/dashboard")
  public ResponseEntity<DashboardDTO> getDashboard() {
    log.debug("Getting dashboard data");
    DashboardDTO dashboard = dashboardService.getDashboard();
    return ResponseEntity.ok(dashboard);
  }
}
