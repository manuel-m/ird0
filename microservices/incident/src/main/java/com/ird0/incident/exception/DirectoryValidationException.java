package com.ird0.incident.exception;

import java.util.UUID;

public class DirectoryValidationException extends RuntimeException {

  public DirectoryValidationException(String entityType, UUID id) {
    super(String.format("%s not found with id: %s", entityType, id));
  }

  public DirectoryValidationException(String message) {
    super(message);
  }
}
