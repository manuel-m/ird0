package com.ird0.portal.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.ird0.portal.config.PortalProperties;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

@Slf4j
@Component
public class IncidentClient {

  private final RestClient restClient;
  private final String baseUrl;

  public IncidentClient(RestClient.Builder restClientBuilder, PortalProperties properties) {
    this.baseUrl = properties.getServices().getIncidentUrl();
    this.restClient = restClientBuilder.baseUrl(baseUrl).build();
  }

  @CircuitBreaker(name = "incidentService", fallbackMethod = "getIncidentsFallback")
  public JsonNode getIncidents(
      UUID policyholderId,
      UUID insurerId,
      String status,
      String type,
      Instant fromDate,
      Instant toDate,
      int page,
      int size,
      String sort) {

    UriComponentsBuilder uriBuilder =
        UriComponentsBuilder.fromPath("/api/v1/incidents")
            .queryParam("page", page)
            .queryParam("size", size);

    if (sort != null) {
      uriBuilder.queryParam("sort", sort);
    }
    if (policyholderId != null) {
      uriBuilder.queryParam("policyholderId", policyholderId);
    }
    if (insurerId != null) {
      uriBuilder.queryParam("insurerId", insurerId);
    }
    if (status != null) {
      uriBuilder.queryParam("status", status);
    }
    if (type != null) {
      uriBuilder.queryParam("type", type);
    }
    if (fromDate != null) {
      uriBuilder.queryParam("fromDate", fromDate);
    }
    if (toDate != null) {
      uriBuilder.queryParam("toDate", toDate);
    }

    return restClient.get().uri(uriBuilder.build().toUriString()).retrieve().body(JsonNode.class);
  }

  @CircuitBreaker(name = "incidentService", fallbackMethod = "getIncidentByIdFallback")
  public JsonNode getIncidentById(UUID id) {
    return restClient.get().uri("/api/v1/incidents/{id}", id).retrieve().body(JsonNode.class);
  }

  @CircuitBreaker(name = "incidentService", fallbackMethod = "createIncidentFallback")
  public JsonNode createIncident(Map<String, Object> request) {
    return restClient
        .post()
        .uri("/api/v1/incidents")
        .contentType(MediaType.APPLICATION_JSON)
        .body(request)
        .retrieve()
        .body(JsonNode.class);
  }

  @CircuitBreaker(name = "incidentService", fallbackMethod = "updateStatusFallback")
  public JsonNode updateStatus(UUID id, Map<String, Object> request) {
    return restClient
        .put()
        .uri("/api/v1/incidents/{id}/status", id)
        .contentType(MediaType.APPLICATION_JSON)
        .body(request)
        .retrieve()
        .body(JsonNode.class);
  }

  @CircuitBreaker(name = "incidentService", fallbackMethod = "assignExpertFallback")
  public JsonNode assignExpert(UUID id, Map<String, Object> request) {
    return restClient
        .post()
        .uri("/api/v1/incidents/{id}/expert", id)
        .contentType(MediaType.APPLICATION_JSON)
        .body(request)
        .retrieve()
        .body(JsonNode.class);
  }

  @CircuitBreaker(name = "incidentService", fallbackMethod = "addCommentFallback")
  public JsonNode addComment(UUID id, Map<String, Object> request) {
    return restClient
        .post()
        .uri("/api/v1/incidents/{id}/comments", id)
        .contentType(MediaType.APPLICATION_JSON)
        .body(request)
        .retrieve()
        .body(JsonNode.class);
  }

  @CircuitBreaker(name = "incidentService", fallbackMethod = "getCommentsFallback")
  public JsonNode getComments(UUID id) {
    return restClient
        .get()
        .uri("/api/v1/incidents/{id}/comments", id)
        .retrieve()
        .body(JsonNode.class);
  }

  @CircuitBreaker(name = "incidentService", fallbackMethod = "getHistoryFallback")
  public JsonNode getHistory(UUID id) {
    return restClient
        .get()
        .uri("/api/v1/incidents/{id}/history", id)
        .retrieve()
        .body(JsonNode.class);
  }

  // Fallback methods
  @SuppressWarnings("unused")
  private JsonNode getIncidentsFallback(
      UUID policyholderId,
      UUID insurerId,
      String status,
      String type,
      Instant fromDate,
      Instant toDate,
      int page,
      int size,
      String sort,
      Throwable t) {
    log.error("Circuit breaker fallback for getIncidents: {}", t.getMessage());
    throw new ServiceUnavailableException("Incident service is currently unavailable");
  }

  @SuppressWarnings("unused")
  private JsonNode getIncidentByIdFallback(UUID id, Throwable t) {
    log.error("Circuit breaker fallback for getIncidentById: {}", t.getMessage());
    throw new ServiceUnavailableException("Incident service is currently unavailable");
  }

  @SuppressWarnings("unused")
  private JsonNode createIncidentFallback(Map<String, Object> request, Throwable t) {
    log.error("Circuit breaker fallback for createIncident: {}", t.getMessage());
    throw new ServiceUnavailableException("Incident service is currently unavailable");
  }

  @SuppressWarnings("unused")
  private JsonNode updateStatusFallback(UUID id, Map<String, Object> request, Throwable t) {
    log.error("Circuit breaker fallback for updateStatus: {}", t.getMessage());
    throw new ServiceUnavailableException("Incident service is currently unavailable");
  }

  @SuppressWarnings("unused")
  private JsonNode assignExpertFallback(UUID id, Map<String, Object> request, Throwable t) {
    log.error("Circuit breaker fallback for assignExpert: {}", t.getMessage());
    throw new ServiceUnavailableException("Incident service is currently unavailable");
  }

  @SuppressWarnings("unused")
  private JsonNode addCommentFallback(UUID id, Map<String, Object> request, Throwable t) {
    log.error("Circuit breaker fallback for addComment: {}", t.getMessage());
    throw new ServiceUnavailableException("Incident service is currently unavailable");
  }

  @SuppressWarnings("unused")
  private JsonNode getCommentsFallback(UUID id, Throwable t) {
    log.error("Circuit breaker fallback for getComments: {}", t.getMessage());
    throw new ServiceUnavailableException("Incident service is currently unavailable");
  }

  @SuppressWarnings("unused")
  private JsonNode getHistoryFallback(UUID id, Throwable t) {
    log.error("Circuit breaker fallback for getHistory: {}", t.getMessage());
    throw new ServiceUnavailableException("Incident service is currently unavailable");
  }

  public static class ServiceUnavailableException extends RuntimeException {
    public ServiceUnavailableException(String message) {
      super(message);
    }
  }
}
