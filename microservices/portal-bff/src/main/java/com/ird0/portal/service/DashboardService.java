package com.ird0.portal.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.ird0.portal.client.IncidentClient;
import com.ird0.portal.dto.response.DashboardDTO;
import com.ird0.portal.dto.response.DashboardDTO.KpiDTO;
import com.ird0.portal.dto.response.DashboardDTO.RecentActivityDTO;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class DashboardService {

  private final IncidentClient incidentClient;

  public DashboardDTO getDashboard() {
    // Fetch all incidents for statistics (first 1000 for now)
    JsonNode allIncidents =
        incidentClient.getIncidents(null, null, null, null, null, null, 0, 1000, "createdAt,desc");

    if (allIncidents == null || !allIncidents.has("content")) {
      return DashboardDTO.builder()
          .kpis(KpiDTO.builder().build())
          .statusDistribution(Map.of())
          .claimsByType(Map.of())
          .recentActivity(List.of())
          .build();
    }

    JsonNode content = allIncidents.get("content");
    long totalClaims =
        allIncidents.has("totalElements") ? allIncidents.get("totalElements").asLong() : 0;

    // Calculate KPIs and distributions
    Map<String, Long> statusDistribution = new HashMap<>();
    Map<String, Long> claimsByType = new HashMap<>();
    List<RecentActivityDTO> recentActivity = new ArrayList<>();
    long pendingCount = 0;
    long inProgressCount = 0;
    long closedThisMonth = 0;

    YearMonth currentMonth = YearMonth.now();
    Instant monthStart = currentMonth.atDay(1).atStartOfDay().toInstant(ZoneOffset.UTC);

    int activityCount = 0;
    for (JsonNode incident : content) {
      String status = incident.has("status") ? incident.get("status").asText() : "UNKNOWN";
      String type = incident.has("type") ? incident.get("type").asText() : "OTHER";

      // Status distribution
      statusDistribution.merge(status, 1L, Long::sum);

      // Type distribution
      claimsByType.merge(type, 1L, Long::sum);

      // Pending count (DECLARED, UNDER_REVIEW)
      if ("DECLARED".equals(status) || "UNDER_REVIEW".equals(status)) {
        pendingCount++;
      }

      // In progress count
      if ("IN_PROGRESS".equals(status) || "QUALIFIED".equals(status)) {
        inProgressCount++;
      }

      // Closed this month
      if ("CLOSED".equals(status) && incident.has("updatedAt")) {
        Instant updatedAt = Instant.parse(incident.get("updatedAt").asText());
        if (updatedAt.isAfter(monthStart)) {
          closedThisMonth++;
        }
      }

      // Recent activity (last 10)
      if (activityCount < 10) {
        String referenceNumber =
            incident.has("referenceNumber") ? incident.get("referenceNumber").asText() : "N/A";
        Instant createdAt =
            incident.has("createdAt") ? Instant.parse(incident.get("createdAt").asText()) : null;

        recentActivity.add(
            RecentActivityDTO.builder()
                .eventType("CLAIM_" + status)
                .description(
                    "Claim " + referenceNumber + " is " + status.toLowerCase().replace("_", " "))
                .claimReference(referenceNumber)
                .occurredAt(createdAt)
                .build());
        activityCount++;
      }
    }

    KpiDTO kpis =
        KpiDTO.builder()
            .totalClaims(totalClaims)
            .pendingCount(pendingCount)
            .inProgressCount(inProgressCount)
            .closedThisMonth(closedThisMonth)
            .build();

    return DashboardDTO.builder()
        .kpis(kpis)
        .statusDistribution(statusDistribution)
        .claimsByType(claimsByType)
        .recentActivity(recentActivity)
        .build();
  }
}
