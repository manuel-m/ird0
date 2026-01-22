package com.ird0.portal.dto.response;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public record DashboardDTO(
    KpiDTO kpis,
    Map<String, Long> statusDistribution,
    Map<String, Long> claimsByType,
    List<RecentActivityDTO> recentActivity) {

  public record KpiDTO(
      long totalClaims, long pendingCount, long inProgressCount, long closedThisMonth) {}

  public record RecentActivityDTO(
      String eventType, String description, String claimReference, Instant occurredAt) {}
}
