package com.ird0.incident.dto;

import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ExpertAssignmentRequest {

  @NotNull(message = "Expert ID is required")
  private UUID expertId;

  private String assignmentReason;

  private Instant scheduledDate;

  private String notes;
}
