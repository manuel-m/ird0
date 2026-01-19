package com.ird0.portal.dto.response;

import java.util.List;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DashboardDTO {

  private KpiDTO kpis;
  private Map<String, Long> statusDistribution;
  private Map<String, Long> claimsByType;
  private List<RecentActivityDTO> recentActivity;

  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  public static class KpiDTO {
    private long totalClaims;
    private long pendingCount;
    private long inProgressCount;
    private long closedThisMonth;
  }

  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  public static class RecentActivityDTO {
    private String eventType;
    private String description;
    private String claimReference;
    private java.time.Instant occurredAt;
  }
}
