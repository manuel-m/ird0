package com.ird0.incident.service;

import com.ird0.incident.config.IncidentProperties;
import com.ird0.incident.model.Incident;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "incident.notification", name = "enabled", havingValue = "true")
public class NotificationClient {

  private final IncidentProperties properties;
  private final RestTemplate restTemplate;
  private final DirectoryValidationService directoryValidationService;

  public void sendIncidentDeclaredNotification(Incident incident) {
    sendNotification(incident, "INCIDENT_DECLARED");
  }

  public void sendIncidentQualifiedNotification(Incident incident) {
    sendNotification(incident, "INCIDENT_QUALIFIED");
  }

  public void sendIncidentAbandonedNotification(Incident incident) {
    sendNotification(incident, "INCIDENT_ABANDONED");
  }

  public void sendExpertAssignedNotification(Incident incident, UUID expertId) {
    String webhookUrl = directoryValidationService.getInsurerWebhookUrl(incident.getInsurerId());
    if (webhookUrl == null) {
      log.warn("No webhook URL for insurer {}, skipping notification", incident.getInsurerId());
      return;
    }

    Map<String, Object> payload =
        Map.of(
            "eventId", UUID.randomUUID().toString(),
            "eventType", "EXPERT_ASSIGNED",
            "timestamp", Instant.now().toString(),
            "incident", buildIncidentPayload(incident),
            "expertId", expertId.toString());

    sendWebhookNotification(webhookUrl, payload);
  }

  private void sendNotification(Incident incident, String eventType) {
    String webhookUrl = directoryValidationService.getInsurerWebhookUrl(incident.getInsurerId());
    if (webhookUrl == null) {
      log.warn("No webhook URL for insurer {}, skipping notification", incident.getInsurerId());
      return;
    }

    Map<String, Object> payload =
        Map.of(
            "eventId", UUID.randomUUID().toString(),
            "eventType", eventType,
            "timestamp", Instant.now().toString(),
            "incident", buildIncidentPayload(incident));

    sendWebhookNotification(webhookUrl, payload);
  }

  private Map<String, Object> buildIncidentPayload(Incident incident) {
    return Map.of(
        "id", incident.getId().toString(),
        "referenceNumber", incident.getReferenceNumber(),
        "policyholderId", incident.getPolicyholderId().toString(),
        "type", incident.getType(),
        "description", incident.getDescription() != null ? incident.getDescription() : "",
        "incidentDate", incident.getIncidentDate().toString(),
        "status", incident.getStatus().name(),
        "estimatedDamage",
            incident.getEstimatedDamage() != null ? incident.getEstimatedDamage().toString() : "0");
  }

  private void sendWebhookNotification(String webhookUrl, Map<String, Object> payload) {
    // For now, send directly to the notification service which will handle the webhook dispatch
    String notificationUrl =
        properties.getNotification().getUrl() + "/api/v1/notifications/webhook";

    Map<String, Object> request = Map.of("webhookUrl", webhookUrl, "payload", payload);

    try {
      restTemplate.postForEntity(notificationUrl, request, Void.class);
      log.info("Notification sent successfully to {}", webhookUrl);
    } catch (RestClientException e) {
      log.error("Failed to send notification: {}", e.getMessage());
      // Don't throw - notifications should not block the main flow
    }
  }
}
