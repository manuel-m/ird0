package com.ird0.notification.service;

import com.ird0.notification.dto.WebhookRequest;
import com.ird0.notification.model.Notification;
import com.ird0.notification.model.NotificationStatus;
import com.ird0.notification.repository.NotificationRepository;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

  private final NotificationRepository notificationRepository;
  private final WebhookDispatcher webhookDispatcher;

  @Transactional
  public Notification createNotification(WebhookRequest request) {
    log.info("Creating notification for webhook URL: {}", request.getWebhookUrl());

    Notification notification = new Notification();
    notification.setWebhookUrl(request.getWebhookUrl());
    notification.setPayload(request.getPayload());

    // Extract event details from payload if present
    Map<String, Object> payload = request.getPayload();
    if (payload != null) {
      if (payload.containsKey("eventType")) {
        notification.setEventType((String) payload.get("eventType"));
      }
      if (payload.containsKey("eventId")) {
        notification.setEventId(UUID.fromString((String) payload.get("eventId")));
      }
      if (payload.containsKey("incident")) {
        @SuppressWarnings("unchecked")
        Map<String, Object> incident = (Map<String, Object>) payload.get("incident");
        if (incident != null && incident.containsKey("id")) {
          notification.setIncidentId(UUID.fromString((String) incident.get("id")));
        }
      }
    }

    if (notification.getEventType() == null) {
      notification.setEventType("WEBHOOK");
    }

    Notification saved = notificationRepository.save(notification);
    log.info("Created notification with id: {}", saved.getId());

    // Optionally dispatch immediately
    // webhookDispatcher.dispatchNotification(saved);

    return saved;
  }

  @Transactional(readOnly = true)
  public Notification getById(UUID id) {
    return notificationRepository
        .findById(id)
        .orElseThrow(() -> new RuntimeException("Notification not found: " + id));
  }

  @Transactional(readOnly = true)
  public List<Notification> getByIncidentId(UUID incidentId) {
    return notificationRepository.findByIncidentId(incidentId);
  }

  @Transactional(readOnly = true)
  public List<Notification> getByStatus(NotificationStatus status) {
    return notificationRepository.findByStatus(status);
  }

  @Transactional
  public Notification retryNotification(UUID id) {
    Notification notification = getById(id);

    if (notification.getStatus() == NotificationStatus.FAILED) {
      notification.setStatus(NotificationStatus.PENDING);
      notification.setNextRetryAt(null);
      notification.setRetryCount(0);
      notification = notificationRepository.save(notification);
      log.info("Reset notification {} for retry", id);
    }

    return notification;
  }

  @Transactional
  public void cancelNotification(UUID id) {
    Notification notification = getById(id);

    if (notification.getStatus() == NotificationStatus.PENDING) {
      notification.setStatus(NotificationStatus.CANCELLED);
      notificationRepository.save(notification);
      log.info("Cancelled notification {}", id);
    }
  }
}
