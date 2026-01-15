package com.ird0.incident.dto;

import com.ird0.incident.model.ExpertAssignment.AssignmentStatus;
import java.time.Instant;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ExpertAssignmentResponse {

  private UUID id;
  private UUID expertId;
  private Instant assignedAt;
  private Instant scheduledDate;
  private AssignmentStatus status;
  private String notes;
}
