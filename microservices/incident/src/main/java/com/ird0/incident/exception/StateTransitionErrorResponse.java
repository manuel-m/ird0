package com.ird0.incident.exception;

import com.ird0.incident.model.IncidentStatus;
import java.time.LocalDateTime;
import lombok.Getter;

@Getter
public class StateTransitionErrorResponse {

  private final int status;
  private final String message;
  private final LocalDateTime timestamp;
  private final String currentStatus;
  private final String requestedStatus;

  public StateTransitionErrorResponse(
      int status, String message, IncidentStatus currentStatus, IncidentStatus requestedStatus) {
    this.status = status;
    this.message = message;
    this.timestamp = LocalDateTime.now();
    this.currentStatus = currentStatus.name();
    this.requestedStatus = requestedStatus.name();
  }
}
