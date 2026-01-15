package com.ird0.incident.exception;

import com.ird0.incident.model.IncidentStatus;
import lombok.Getter;

@Getter
public class InvalidStateTransitionException extends RuntimeException {

  private final IncidentStatus currentStatus;
  private final IncidentStatus requestedStatus;

  public InvalidStateTransitionException(
      IncidentStatus currentStatus, IncidentStatus requestedStatus) {
    super(
        String.format(
            "Cannot transition from %s to %s", currentStatus.name(), requestedStatus.name()));
    this.currentStatus = currentStatus;
    this.requestedStatus = requestedStatus;
  }
}
