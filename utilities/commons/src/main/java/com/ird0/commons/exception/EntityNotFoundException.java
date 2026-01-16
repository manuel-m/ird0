package com.ird0.commons.exception;

import java.util.UUID;
import org.springframework.http.HttpStatus;

public class EntityNotFoundException extends BaseException {

  public EntityNotFoundException(String message) {
    super(message, HttpStatus.NOT_FOUND);
  }

  public EntityNotFoundException(String entityType, UUID id) {
    super(entityType + " not found with id: " + id, HttpStatus.NOT_FOUND);
  }

  public EntityNotFoundException(String entityType, String identifier) {
    super(entityType + " not found with identifier: " + identifier, HttpStatus.NOT_FOUND);
  }
}
