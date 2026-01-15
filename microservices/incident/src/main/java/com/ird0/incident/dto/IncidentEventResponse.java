package com.ird0.incident.dto;

import com.ird0.incident.model.IncidentStatus;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class IncidentEventResponse {

  private UUID id;
  private String eventType;
  private IncidentStatus previousStatus;
  private IncidentStatus newStatus;
  private Map<String, Object> payload;
  private UUID triggeredBy;
  private Instant occurredAt;
}
