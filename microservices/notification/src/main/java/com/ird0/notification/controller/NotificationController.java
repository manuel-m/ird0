package com.ird0.notification.controller;

import com.ird0.notification.dto.NotificationResponse;
import com.ird0.notification.dto.WebhookRequest;
import com.ird0.notification.model.Notification;
import com.ird0.notification.model.NotificationStatus;
import com.ird0.notification.service.NotificationService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("${notification.api.base-path:/api/v1/notifications}")
@RequiredArgsConstructor
public class NotificationController {

  private final NotificationService notificationService;

  @PostMapping("/webhook")
  public ResponseEntity<NotificationResponse> createWebhookNotification(
      @Valid @RequestBody WebhookRequest request) {
    Notification notification = notificationService.createNotification(request);
    return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(notification));
  }

  @GetMapping("/{id}")
  public ResponseEntity<NotificationResponse> getNotification(@PathVariable UUID id) {
    Notification notification = notificationService.getById(id);
    return ResponseEntity.ok(toResponse(notification));
  }

  @GetMapping
  public ResponseEntity<List<NotificationResponse>> getNotifications(
      @RequestParam(required = false) UUID incidentId,
      @RequestParam(required = false) NotificationStatus status) {
    List<Notification> notifications;

    if (incidentId != null) {
      notifications = notificationService.getByIncidentId(incidentId);
    } else if (status != null) {
      notifications = notificationService.getByStatus(status);
    } else {
      notifications = notificationService.getByStatus(NotificationStatus.PENDING);
    }

    List<NotificationResponse> response = notifications.stream().map(this::toResponse).toList();
    return ResponseEntity.ok(response);
  }

  @PostMapping("/{id}/retry")
  public ResponseEntity<NotificationResponse> retryNotification(@PathVariable UUID id) {
    Notification notification = notificationService.retryNotification(id);
    return ResponseEntity.ok(toResponse(notification));
  }

  @DeleteMapping("/{id}")
  public ResponseEntity<Void> cancelNotification(@PathVariable UUID id) {
    notificationService.cancelNotification(id);
    return ResponseEntity.noContent().build();
  }

  private NotificationResponse toResponse(Notification notification) {
    NotificationResponse response = new NotificationResponse();
    response.setId(notification.getId());
    response.setEventId(notification.getEventId());
    response.setEventType(notification.getEventType());
    response.setIncidentId(notification.getIncidentId());
    response.setWebhookUrl(notification.getWebhookUrl());
    response.setStatus(notification.getStatus());
    response.setPayload(notification.getPayload());
    response.setSentAt(notification.getSentAt());
    response.setResponseCode(notification.getResponseCode());
    response.setRetryCount(notification.getRetryCount());
    response.setNextRetryAt(notification.getNextRetryAt());
    response.setFailureReason(notification.getFailureReason());
    response.setCreatedAt(notification.getCreatedAt());
    return response;
  }
}
