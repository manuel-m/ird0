package com.ird0.portal.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.ird0.portal.config.PortalProperties;
import com.ird0.portal.dto.response.ActorDTO;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Slf4j
@Component
public class DirectoryClient {

  private final RestClient policyholdersClient;
  private final RestClient expertsClient;
  private final RestClient insurersClient;

  // Simple cache for directory lookups to reduce repeated calls
  private final Map<String, ActorDTO> cache = new ConcurrentHashMap<>();

  public DirectoryClient(RestClient.Builder restClientBuilder, PortalProperties properties) {
    this.policyholdersClient =
        restClientBuilder.baseUrl(properties.getServices().getPolicyholdersUrl()).build();
    this.expertsClient =
        restClientBuilder.baseUrl(properties.getServices().getExpertsUrl()).build();
    this.insurersClient =
        restClientBuilder.baseUrl(properties.getServices().getInsurersUrl()).build();
  }

  @CircuitBreaker(name = "directoryService", fallbackMethod = "getPolicyholderFallback")
  public ActorDTO getPolicyholder(UUID id) {
    String cacheKey = "policyholder:" + id;
    return cache.computeIfAbsent(
        cacheKey,
        k -> {
          JsonNode response =
              policyholdersClient
                  .get()
                  .uri("/api/policyholders/{id}", id)
                  .retrieve()
                  .body(JsonNode.class);
          return mapToActorDTO(response);
        });
  }

  @CircuitBreaker(name = "directoryService", fallbackMethod = "getExpertFallback")
  public ActorDTO getExpert(UUID id) {
    String cacheKey = "expert:" + id;
    return cache.computeIfAbsent(
        cacheKey,
        k -> {
          JsonNode response =
              expertsClient.get().uri("/api/experts/{id}", id).retrieve().body(JsonNode.class);
          return mapToActorDTO(response);
        });
  }

  @CircuitBreaker(name = "directoryService", fallbackMethod = "getInsurerFallback")
  public ActorDTO getInsurer(UUID id) {
    String cacheKey = "insurer:" + id;
    return cache.computeIfAbsent(
        cacheKey,
        k -> {
          JsonNode response =
              insurersClient.get().uri("/api/insurers/{id}", id).retrieve().body(JsonNode.class);
          return mapToActorDTO(response);
        });
  }

  @CircuitBreaker(name = "directoryService", fallbackMethod = "getAllPolicyholdersFallback")
  public List<ActorDTO> getAllPolicyholders() {
    JsonNode response =
        policyholdersClient.get().uri("/api/policyholders").retrieve().body(JsonNode.class);
    return mapToActorDTOList(response);
  }

  @CircuitBreaker(name = "directoryService", fallbackMethod = "getAllExpertsFallback")
  public List<ActorDTO> getAllExperts() {
    JsonNode response = expertsClient.get().uri("/api/experts").retrieve().body(JsonNode.class);
    return mapToActorDTOList(response);
  }

  @CircuitBreaker(name = "directoryService", fallbackMethod = "getAllInsurersFallback")
  public List<ActorDTO> getAllInsurers() {
    JsonNode response = insurersClient.get().uri("/api/insurers").retrieve().body(JsonNode.class);
    return mapToActorDTOList(response);
  }

  public void clearCache() {
    cache.clear();
  }

  private ActorDTO mapToActorDTO(JsonNode node) {
    if (node == null) {
      return null;
    }
    return ActorDTO.builder()
        .id(node.has("id") ? UUID.fromString(node.get("id").asText()) : null)
        .name(node.has("name") ? node.get("name").asText() : null)
        .type(node.has("type") ? node.get("type").asText() : null)
        .email(node.has("email") ? node.get("email").asText() : null)
        .phone(node.has("phone") ? node.get("phone").asText() : null)
        .address(node.has("address") ? node.get("address").asText() : null)
        .build();
  }

  private List<ActorDTO> mapToActorDTOList(JsonNode node) {
    if (node == null || !node.isArray()) {
      return List.of();
    }
    return java.util.stream.StreamSupport.stream(node.spliterator(), false)
        .map(this::mapToActorDTO)
        .toList();
  }

  // Fallback methods
  @SuppressWarnings("unused")
  private ActorDTO getPolicyholderFallback(UUID id, Throwable t) {
    log.warn("Circuit breaker fallback for getPolicyholder {}: {}", id, t.getMessage());
    return ActorDTO.builder().id(id).name("Unknown Policyholder").build();
  }

  @SuppressWarnings("unused")
  private ActorDTO getExpertFallback(UUID id, Throwable t) {
    log.warn("Circuit breaker fallback for getExpert {}: {}", id, t.getMessage());
    return ActorDTO.builder().id(id).name("Unknown Expert").build();
  }

  @SuppressWarnings("unused")
  private ActorDTO getInsurerFallback(UUID id, Throwable t) {
    log.warn("Circuit breaker fallback for getInsurer {}: {}", id, t.getMessage());
    return ActorDTO.builder().id(id).name("Unknown Insurer").build();
  }

  @SuppressWarnings("unused")
  private List<ActorDTO> getAllPolicyholdersFallback(Throwable t) {
    log.warn("Circuit breaker fallback for getAllPolicyholders: {}", t.getMessage());
    return List.of();
  }

  @SuppressWarnings("unused")
  private List<ActorDTO> getAllExpertsFallback(Throwable t) {
    log.warn("Circuit breaker fallback for getAllExperts: {}", t.getMessage());
    return List.of();
  }

  @SuppressWarnings("unused")
  private List<ActorDTO> getAllInsurersFallback(Throwable t) {
    log.warn("Circuit breaker fallback for getAllInsurers: {}", t.getMessage());
    return List.of();
  }
}
