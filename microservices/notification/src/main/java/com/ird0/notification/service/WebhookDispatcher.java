package com.ird0.notification.service;

import com.ird0.notification.config.NotificationProperties;
import com.ird0.notification.model.Notification;
import com.ird0.notification.repository.NotificationRepository;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

@Slf4j
@Service
@RequiredArgsConstructor
public class WebhookDispatcher {

  private final NotificationRepository notificationRepository;
  private final NotificationProperties properties;
  private final RestTemplate restTemplate;

  @Scheduled(
      fixedDelayString = "${notification.scheduler.fixed-delay:30000}",
      initialDelayString = "${notification.scheduler.initial-delay:10000}")
  public void processNotifications() {
    List<Notification> pendingNotifications = notificationRepository.findReadyToSend(Instant.now());

    if (!pendingNotifications.isEmpty()) {
      log.info("Processing {} pending notifications", pendingNotifications.size());
    }

    for (Notification notification : pendingNotifications) {
      try {
        dispatchNotification(notification);
      } catch (Exception e) {
        log.error(
            "Unexpected error dispatching notification {}: {}",
            notification.getId(),
            e.getMessage());
      }
    }
  }

  @Transactional
  public void dispatchNotification(Notification notification) {
    log.info(
        "Dispatching notification {} to {}", notification.getId(), notification.getWebhookUrl());

    try {
      HttpHeaders headers = new HttpHeaders();
      headers.setContentType(MediaType.APPLICATION_JSON);
      headers.set("X-Notification-ID", notification.getId().toString());
      headers.set("X-Event-ID", notification.getEventId().toString());
      headers.set("X-Event-Type", notification.getEventType());

      HttpEntity<Object> request = new HttpEntity<>(notification.getPayload(), headers);

      ResponseEntity<String> response =
          restTemplate.postForEntity(notification.getWebhookUrl(), request, String.class);

      handleSuccessResponse(notification, response);

    } catch (HttpClientErrorException e) {
      // 4xx errors - client error, no retry
      handleClientError(notification, e);
    } catch (HttpServerErrorException e) {
      // 5xx errors - server error, retry
      handleServerError(notification, e);
    } catch (ResourceAccessException e) {
      // Connection error - retry
      handleConnectionError(notification, e);
    } catch (Exception e) {
      handleUnexpectedError(notification, e);
    }

    notificationRepository.save(notification);
  }

  private void handleSuccessResponse(Notification notification, ResponseEntity<String> response) {
    int statusCode = response.getStatusCode().value();
    String body = response.getBody();

    if (response.getStatusCode().is2xxSuccessful()) {
      notification.markAsDelivered();
      notification.setResponseCode(statusCode);
      notification.setResponseBody(body);
      log.info(
          "Notification {} delivered successfully to {}",
          notification.getId(),
          notification.getWebhookUrl());
    } else {
      notification.markAsSent(statusCode, body);
      log.warn(
          "Notification {} sent but received non-2xx response: {}",
          notification.getId(),
          statusCode);
    }
  }

  private void handleClientError(Notification notification, HttpClientErrorException e) {
    int statusCode = e.getStatusCode().value();
    String responseBody = e.getResponseBodyAsString();

    log.warn(
        "Client error for notification {}: {} - {}",
        notification.getId(),
        statusCode,
        e.getMessage());

    // Don't retry client errors (4xx) - they won't succeed
    notification.markAsFailed("Client error: " + statusCode + " - " + e.getMessage());
    notification.setResponseCode(statusCode);
    notification.setResponseBody(responseBody);
  }

  private void handleServerError(Notification notification, HttpServerErrorException e) {
    int statusCode = e.getStatusCode().value();
    String responseBody = e.getResponseBodyAsString();

    log.warn(
        "Server error for notification {}: {} - {}",
        notification.getId(),
        statusCode,
        e.getMessage());

    notification.setResponseCode(statusCode);
    notification.setResponseBody(responseBody);

    scheduleRetryOrFail(notification, "Server error: " + statusCode);
  }

  private void handleConnectionError(Notification notification, ResourceAccessException e) {
    log.warn("Connection error for notification {}: {}", notification.getId(), e.getMessage());

    scheduleRetryOrFail(notification, "Connection error: " + e.getMessage());
  }

  private void handleUnexpectedError(Notification notification, Exception e) {
    log.error("Unexpected error for notification {}: {}", notification.getId(), e.getMessage());

    scheduleRetryOrFail(notification, "Unexpected error: " + e.getMessage());
  }

  private void scheduleRetryOrFail(Notification notification, String reason) {
    int currentRetries = notification.getRetryCount() != null ? notification.getRetryCount() : 0;
    int maxAttempts = properties.getRetry().getMaxAttempts();

    if (currentRetries >= maxAttempts) {
      notification.markAsFailed(reason + " (max retries exceeded)");
      log.warn(
          "Notification {} failed after {} retries: {}",
          notification.getId(),
          currentRetries,
          reason);
    } else {
      long delay = calculateRetryDelay(currentRetries);
      Instant nextRetry = Instant.now().plusMillis(delay);
      notification.incrementRetry(nextRetry);
      notification.setFailureReason(reason);

      log.info(
          "Scheduling retry {} for notification {} at {}",
          currentRetries + 1,
          notification.getId(),
          nextRetry);
    }
  }

  private long calculateRetryDelay(int retryCount) {
    double initialDelay = properties.getRetry().getInitialDelay();
    double multiplier = properties.getRetry().getBackoffMultiplier();
    long maxDelay = properties.getRetry().getMaxDelay();

    long delay = (long) (initialDelay * Math.pow(multiplier, retryCount));
    return Math.min(delay, maxDelay);
  }
}
