package com.ird0.incident.service;

import com.ird0.incident.config.IncidentProperties;
import com.ird0.incident.exception.DirectoryValidationException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

@Slf4j
@Service
@RequiredArgsConstructor
public class DirectoryValidationService {

  private static final String DIRECTORY_SERVICE = "directoryService";

  private final IncidentProperties properties;
  private final RestTemplate restTemplate;

  @CircuitBreaker(name = DIRECTORY_SERVICE, fallbackMethod = "validatePolicyholderFallback")
  public void validatePolicyholder(UUID policyholderId) {
    validateEntity("Policyholder", properties.getDirectory().getPolicyholdersUrl(), policyholderId);
  }

  @CircuitBreaker(name = DIRECTORY_SERVICE, fallbackMethod = "validateInsurerFallback")
  public void validateInsurer(UUID insurerId) {
    validateEntity("Insurer", properties.getDirectory().getInsurersUrl(), insurerId);
  }

  @CircuitBreaker(name = DIRECTORY_SERVICE, fallbackMethod = "validateExpertFallback")
  public void validateExpert(UUID expertId) {
    validateEntity("Expert", properties.getDirectory().getExpertsUrl(), expertId);
  }

  @CircuitBreaker(name = DIRECTORY_SERVICE, fallbackMethod = "validateProviderFallback")
  public void validateProvider(UUID providerId) {
    validateEntity("Provider", properties.getDirectory().getProvidersUrl(), providerId);
  }

  @CircuitBreaker(name = DIRECTORY_SERVICE, fallbackMethod = "getInsurerWebhookUrlFallback")
  public String getInsurerWebhookUrl(UUID insurerId) {
    String url = properties.getDirectory().getInsurersUrl() + "/api/insurers/" + insurerId;
    try {
      InsurerResponse response = restTemplate.getForObject(url, InsurerResponse.class);
      if (response != null && response.getWebhookUrl() != null) {
        return response.getWebhookUrl();
      }
      log.warn("Insurer {} does not have a webhook URL configured", insurerId);
      return null;
    } catch (HttpClientErrorException e) {
      if (e.getStatusCode() == HttpStatus.NOT_FOUND) {
        throw new DirectoryValidationException("Insurer", insurerId);
      }
      throw new DirectoryValidationException("Failed to fetch insurer details: " + e.getMessage());
    } catch (RestClientException e) {
      log.error("Error fetching insurer webhook URL: {}", e.getMessage());
      throw new DirectoryValidationException(
          "Unable to connect to insurer service: " + e.getMessage());
    }
  }

  private void validateEntity(String entityType, String baseUrl, UUID id) {
    String url = baseUrl + "/api/" + entityType.toLowerCase() + "s/" + id;
    log.debug("Validating {} at URL: {}", entityType, url);

    try {
      restTemplate.headForHeaders(url);
      log.debug("{} {} validated successfully", entityType, id);
    } catch (HttpClientErrorException e) {
      if (e.getStatusCode() == HttpStatus.NOT_FOUND) {
        throw new DirectoryValidationException(entityType, id);
      }
      throw new DirectoryValidationException(
          "Failed to validate " + entityType + ": " + e.getMessage());
    } catch (RestClientException e) {
      log.error("Error validating {}: {}", entityType, e.getMessage());
      throw new DirectoryValidationException(
          "Unable to connect to " + entityType + " service: " + e.getMessage());
    }
  }

  @SuppressWarnings("java:S1144") // Used by Resilience4j @CircuitBreaker via reflection
  private void validatePolicyholderFallback(UUID policyholderId, Exception e) {
    log.warn(
        "Circuit breaker open for directory service. Skipping policyholder validation for: {}. Error: {}",
        policyholderId,
        e.getMessage());
    throw new DirectoryValidationException(
        "Directory service unavailable. Cannot validate policyholder.");
  }

  @SuppressWarnings("java:S1144") // Used by Resilience4j @CircuitBreaker via reflection
  private void validateInsurerFallback(UUID insurerId, Exception e) {
    log.warn(
        "Circuit breaker open for directory service. Skipping insurer validation for: {}. Error: {}",
        insurerId,
        e.getMessage());
    throw new DirectoryValidationException(
        "Directory service unavailable. Cannot validate insurer.");
  }

  @SuppressWarnings("java:S1144") // Used by Resilience4j @CircuitBreaker via reflection
  private void validateExpertFallback(UUID expertId, Exception e) {
    log.warn(
        "Circuit breaker open for directory service. Skipping expert validation for: {}. Error: {}",
        expertId,
        e.getMessage());
    throw new DirectoryValidationException(
        "Directory service unavailable. Cannot validate expert.");
  }

  @SuppressWarnings("java:S1144") // Used by Resilience4j @CircuitBreaker via reflection
  private void validateProviderFallback(UUID providerId, Exception e) {
    log.warn(
        "Circuit breaker open for directory service. Skipping provider validation for: {}. Error: {}",
        providerId,
        e.getMessage());
    throw new DirectoryValidationException(
        "Directory service unavailable. Cannot validate provider.");
  }

  @SuppressWarnings("java:S1144") // Used by Resilience4j @CircuitBreaker via reflection
  private String getInsurerWebhookUrlFallback(UUID insurerId, Exception e) {
    log.warn(
        "Circuit breaker open for directory service. Cannot fetch webhook URL for insurer: {}. Error: {}",
        insurerId,
        e.getMessage());
    return null;
  }

  private static class InsurerResponse {
    private UUID id;
    private String name;
    private String webhookUrl;

    public UUID getId() {
      return id;
    }

    public void setId(UUID id) {
      this.id = id;
    }

    public String getName() {
      return name;
    }

    public void setName(String name) {
      this.name = name;
    }

    public String getWebhookUrl() {
      return webhookUrl;
    }

    public void setWebhookUrl(String webhookUrl) {
      this.webhookUrl = webhookUrl;
    }
  }
}
