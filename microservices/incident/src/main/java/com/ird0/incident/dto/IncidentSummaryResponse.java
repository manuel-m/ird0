package com.ird0.incident.dto;

import com.ird0.incident.model.IncidentStatus;
import java.time.Instant;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class IncidentSummaryResponse {

  private UUID id;
  private String referenceNumber;
  private UUID policyholderId;
  private IncidentStatus status;
  private String type;
  private Instant incidentDate;
  private Instant createdAt;
}
