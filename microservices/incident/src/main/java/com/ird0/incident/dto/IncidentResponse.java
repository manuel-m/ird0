package com.ird0.incident.dto;

import com.ird0.incident.model.IncidentStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class IncidentResponse {

  private UUID id;
  private String referenceNumber;
  private UUID policyholderId;
  private UUID insurerId;
  private IncidentStatus status;
  private String type;
  private String description;
  private Instant incidentDate;
  private LocationDTO location;
  private BigDecimal estimatedDamage;
  private String currency;
  private Instant createdAt;
  private Instant updatedAt;
  private List<ExpertAssignmentResponse> expertAssignments;
  private List<CommentResponse> comments;
}
