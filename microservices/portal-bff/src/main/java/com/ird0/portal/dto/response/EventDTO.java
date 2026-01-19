package com.ird0.portal.dto.response;

import java.time.Instant;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EventDTO {

  private UUID id;
  private String eventType;
  private String description;
  private String oldValue;
  private String newValue;
  private UUID actorId;
  private String actorName;
  private Instant occurredAt;
}
