package com.ird0.notification.controller;

import com.ird0.notification.dto.NotificationResponse;
import com.ird0.notification.dto.WebhookRequest;
import com.ird0.notification.model.Notification;
import com.ird0.notification.model.NotificationStatus;
import com.ird0.notification.service.NotificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@Tag(name = "Notifications", description = "Webhook notification management")
public class NotificationController {

  private final NotificationService notificationService;

  @Operation(summary = "Create webhook notification", operationId = "createWebhookNotification")
  @ApiResponse(responseCode = "201", description = "Notification created")
  @PostMapping("/webhook")
  public ResponseEntity<NotificationResponse> createWebhookNotification(
      @Valid @RequestBody WebhookRequest request) {
    Notification notification = notificationService.createNotification(request);
    return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(notification));
  }

  @Operation(summary = "Get notification by ID", operationId = "getNotificationById")
  @ApiResponse(responseCode = "200", description = "Notification found")
  @ApiResponse(responseCode = "404", description = "Notification not found")
  @GetMapping("/{id}")
  public ResponseEntity<NotificationResponse> getNotification(@PathVariable UUID id) {
    Notification notification = notificationService.getById(id);
    return ResponseEntity.ok(toResponse(notification));
  }

  @Operation(summary = "List notifications with filters", operationId = "getNotifications")
  @ApiResponse(responseCode = "200", description = "List of notifications")
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

  @Operation(summary = "Retry failed notification", operationId = "retryNotification")
  @ApiResponse(responseCode = "200", description = "Notification retried")
  @ApiResponse(responseCode = "404", description = "Notification not found")
  @PostMapping("/{id}/retry")
  public ResponseEntity<NotificationResponse> retryNotification(@PathVariable UUID id) {
    Notification notification = notificationService.retryNotification(id);
    return ResponseEntity.ok(toResponse(notification));
  }

  @Operation(summary = "Cancel pending notification", operationId = "cancelNotification")
  @ApiResponse(responseCode = "204", description = "Notification cancelled")
  @ApiResponse(responseCode = "404", description = "Notification not found")
  @DeleteMapping("/{id}")
  public ResponseEntity<Void> cancelNotification(@PathVariable UUID id) {
    notificationService.cancelNotification(id);
    return ResponseEntity.noContent().build();
  }

  private NotificationResponse toResponse(Notification notification) {
    return new NotificationResponse(
        notification.getId(),
        notification.getEventId(),
        notification.getEventType(),
        notification.getIncidentId(),
        notification.getWebhookUrl(),
        notification.getStatus(),
        notification.getPayload(),
        notification.getSentAt(),
        notification.getResponseCode(),
        notification.getRetryCount(),
        notification.getNextRetryAt(),
        notification.getFailureReason(),
        notification.getCreatedAt());
  }
}
