package com.ird0.portal.dto.request;

import jakarta.validation.constraints.NotNull;
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
public class ExpertAssignmentRequestDTO {

  @NotNull(message = "Expert ID is required")
  private UUID expertId;

  private Instant scheduledDate;

  private String notes;
}
