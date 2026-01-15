package com.ird0.incident.dto;

import com.ird0.incident.model.IncidentStatus;
import jakarta.validation.constraints.NotNull;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class StatusUpdateRequest {

  @NotNull(message = "Status is required")
  private IncidentStatus status;

  private String reason;

  private Map<String, Object> qualificationDetails;
}
