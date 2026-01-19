package com.ird0.portal.controller;

import com.ird0.portal.dto.request.CommentRequestDTO;
import com.ird0.portal.dto.request.CreateClaimRequestDTO;
import com.ird0.portal.dto.request.ExpertAssignmentRequestDTO;
import com.ird0.portal.dto.request.StatusUpdateRequestDTO;
import com.ird0.portal.dto.response.ActorDTO;
import com.ird0.portal.dto.response.ClaimDetailDTO;
import com.ird0.portal.dto.response.ClaimSummaryDTO;
import com.ird0.portal.dto.response.CommentDTO;
import com.ird0.portal.dto.response.EventDTO;
import com.ird0.portal.service.ClaimsAggregationService;
import jakarta.validation.Valid;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("${portal.api.base-path:/api/portal/v1}")
@RequiredArgsConstructor
public class ClaimsController {

  private final ClaimsAggregationService claimsService;

  @GetMapping("/claims")
  public ResponseEntity<Page<ClaimSummaryDTO>> getClaims(
      @RequestParam(required = false) UUID policyholderId,
      @RequestParam(required = false) UUID insurerId,
      @RequestParam(required = false) String status,
      @RequestParam(required = false) String type,
      @RequestParam(required = false) Instant fromDate,
      @RequestParam(required = false) Instant toDate,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size,
      @RequestParam(defaultValue = "createdAt,desc") String sort) {

    log.debug("Getting claims with filters - status: {}, type: {}, page: {}", status, type, page);
    Page<ClaimSummaryDTO> claims =
        claimsService.getClaims(
            policyholderId, insurerId, status, type, fromDate, toDate, page, size, sort);
    return ResponseEntity.ok(claims);
  }

  @GetMapping("/claims/{id}")
  public ResponseEntity<ClaimDetailDTO> getClaimById(@PathVariable UUID id) {
    log.debug("Getting claim detail for id: {}", id);
    ClaimDetailDTO claim = claimsService.getClaimById(id);
    if (claim == null) {
      return ResponseEntity.notFound().build();
    }
    return ResponseEntity.ok(claim);
  }

  @PostMapping("/claims")
  public ResponseEntity<ClaimDetailDTO> createClaim(
      @Valid @RequestBody CreateClaimRequestDTO request) {
    log.info("Creating new claim of type: {}", request.getType());
    ClaimDetailDTO claim = claimsService.createClaim(request);
    return ResponseEntity.status(HttpStatus.CREATED).body(claim);
  }

  @PutMapping("/claims/{id}/status")
  public ResponseEntity<ClaimDetailDTO> updateStatus(
      @PathVariable UUID id, @Valid @RequestBody StatusUpdateRequestDTO request) {
    log.info("Updating status for claim {}: {}", id, request.getStatus());
    ClaimDetailDTO claim = claimsService.updateStatus(id, request);
    return ResponseEntity.ok(claim);
  }

  @PostMapping("/claims/{id}/expert")
  public ResponseEntity<ClaimDetailDTO> assignExpert(
      @PathVariable UUID id, @Valid @RequestBody ExpertAssignmentRequestDTO request) {
    log.info("Assigning expert {} to claim {}", request.getExpertId(), id);
    ClaimDetailDTO claim = claimsService.assignExpert(id, request);
    return ResponseEntity.ok(claim);
  }

  @GetMapping("/claims/{id}/comments")
  public ResponseEntity<List<CommentDTO>> getComments(@PathVariable UUID id) {
    log.debug("Getting comments for claim: {}", id);
    List<CommentDTO> comments = claimsService.getComments(id);
    return ResponseEntity.ok(comments);
  }

  @PostMapping("/claims/{id}/comments")
  public ResponseEntity<ClaimDetailDTO> addComment(
      @PathVariable UUID id, @Valid @RequestBody CommentRequestDTO request) {
    log.info("Adding comment to claim: {}", id);
    ClaimDetailDTO claim = claimsService.addComment(id, request);
    return ResponseEntity.ok(claim);
  }

  @GetMapping("/claims/{id}/history")
  public ResponseEntity<List<EventDTO>> getHistory(@PathVariable UUID id) {
    log.debug("Getting history for claim: {}", id);
    List<EventDTO> history = claimsService.getHistory(id);
    return ResponseEntity.ok(history);
  }

  @GetMapping("/experts")
  public ResponseEntity<List<ActorDTO>> getExperts() {
    log.debug("Getting available experts");
    List<ActorDTO> experts = claimsService.getExperts();
    return ResponseEntity.ok(experts);
  }

  @GetMapping("/policyholders")
  public ResponseEntity<List<ActorDTO>> getPolicyholders() {
    log.debug("Getting policyholders");
    List<ActorDTO> policyholders = claimsService.getPolicyholders();
    return ResponseEntity.ok(policyholders);
  }

  @GetMapping("/insurers")
  public ResponseEntity<List<ActorDTO>> getInsurers() {
    log.debug("Getting insurers");
    List<ActorDTO> insurers = claimsService.getInsurers();
    return ResponseEntity.ok(insurers);
  }
}
