package com.ird0.incident.exception;

import java.util.UUID;

public class IncidentNotFoundException extends RuntimeException {

  public IncidentNotFoundException(UUID id) {
    super("Incident not found with id: " + id);
  }

  public IncidentNotFoundException(String referenceNumber) {
    super("Incident not found with reference number: " + referenceNumber);
  }
}
