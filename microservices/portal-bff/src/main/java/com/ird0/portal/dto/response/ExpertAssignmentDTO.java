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
public class ExpertAssignmentDTO {

  private UUID id;
  private ActorDTO expert;
  private Instant scheduledDate;
  private String notes;
  private Instant assignedAt;
}
