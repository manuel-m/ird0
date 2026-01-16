package com.ird0.notification.exception;

import com.ird0.commons.exception.EntityNotFoundException;
import java.util.UUID;

@SuppressWarnings("java:S110") // Inheritance depth is intentional for exception hierarchy
public class NotificationNotFoundException extends EntityNotFoundException {

  public NotificationNotFoundException(UUID id) {
    super("Notification", id);
  }
}
