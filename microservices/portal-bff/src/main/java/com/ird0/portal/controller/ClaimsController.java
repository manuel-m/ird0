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
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("${portal.api.base-path:/api/portal/v1}")
@RequiredArgsConstructor
@Tag(name = "Claims", description = "Claims management operations")
public class ClaimsController {

  private final ClaimsAggregationService claimsService;

  @Operation(summary = "List claims with filters", operationId = "getClaims")
  @ApiResponse(responseCode = "200", description = "Paginated list of claims")
  @GetMapping("/claims")
  @PreAuthorize("hasRole('claims-viewer')")
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

  @Operation(summary = "Get claim by ID", operationId = "getClaimById")
  @ApiResponse(responseCode = "200", description = "Claim found")
  @ApiResponse(responseCode = "404", description = "Claim not found")
  @GetMapping("/claims/{id}")
  @PreAuthorize("hasRole('claims-viewer')")
  public ResponseEntity<ClaimDetailDTO> getClaimById(@PathVariable UUID id) {
    log.debug("Getting claim detail for id: {}", id);
    ClaimDetailDTO claim = claimsService.getClaimById(id);
    if (claim == null) {
      return ResponseEntity.notFound().build();
    }
    return ResponseEntity.ok(claim);
  }

  @Operation(summary = "Create new claim", operationId = "createClaim")
  @ApiResponse(responseCode = "201", description = "Claim created")
  @PostMapping("/claims")
  @PreAuthorize("hasRole('claims-manager')")
  public ResponseEntity<ClaimDetailDTO> createClaim(
      @Valid @RequestBody CreateClaimRequestDTO request) {
    log.info("Creating new claim of type: {}", request.type());
    ClaimDetailDTO claim = claimsService.createClaim(request);
    return ResponseEntity.status(HttpStatus.CREATED).body(claim);
  }

  @Operation(summary = "Update claim status", operationId = "updateClaimStatus")
  @ApiResponse(responseCode = "200", description = "Status updated")
  @PutMapping("/claims/{id}/status")
  @PreAuthorize("hasRole('claims-manager')")
  public ResponseEntity<ClaimDetailDTO> updateStatus(
      @PathVariable UUID id, @Valid @RequestBody StatusUpdateRequestDTO request) {
    log.info("Updating status for claim {}: {}", id, request.status());
    ClaimDetailDTO claim = claimsService.updateStatus(id, request);
    return ResponseEntity.ok(claim);
  }

  @Operation(summary = "Assign expert to claim", operationId = "assignExpert")
  @ApiResponse(responseCode = "200", description = "Expert assigned")
  @PostMapping("/claims/{id}/expert")
  @PreAuthorize("hasRole('claims-manager')")
  public ResponseEntity<ClaimDetailDTO> assignExpert(
      @PathVariable UUID id, @Valid @RequestBody ExpertAssignmentRequestDTO request) {
    log.info("Assigning expert {} to claim {}", request.expertId(), id);
    ClaimDetailDTO claim = claimsService.assignExpert(id, request);
    return ResponseEntity.ok(claim);
  }

  @Operation(summary = "Get claim comments", operationId = "getClaimComments")
  @ApiResponse(responseCode = "200", description = "List of comments")
  @GetMapping("/claims/{id}/comments")
  @PreAuthorize("hasRole('claims-viewer')")
  public ResponseEntity<List<CommentDTO>> getComments(@PathVariable UUID id) {
    log.debug("Getting comments for claim: {}", id);
    List<CommentDTO> comments = claimsService.getComments(id);
    return ResponseEntity.ok(comments);
  }

  @Operation(summary = "Add comment to claim", operationId = "addClaimComment")
  @ApiResponse(responseCode = "200", description = "Comment added")
  @PostMapping("/claims/{id}/comments")
  @PreAuthorize("hasRole('claims-manager')")
  public ResponseEntity<ClaimDetailDTO> addComment(
      @PathVariable UUID id, @Valid @RequestBody CommentRequestDTO request) {
    log.info("Adding comment to claim: {}", id);
    ClaimDetailDTO claim = claimsService.addComment(id, request);
    return ResponseEntity.ok(claim);
  }

  @Operation(summary = "Get claim event history", operationId = "getClaimHistory")
  @ApiResponse(responseCode = "200", description = "Event history")
  @GetMapping("/claims/{id}/history")
  @PreAuthorize("hasRole('claims-viewer')")
  public ResponseEntity<List<EventDTO>> getHistory(@PathVariable UUID id) {
    log.debug("Getting history for claim: {}", id);
    List<EventDTO> history = claimsService.getHistory(id);
    return ResponseEntity.ok(history);
  }

  @Operation(summary = "Get available experts", operationId = "getExperts")
  @ApiResponse(responseCode = "200", description = "List of experts")
  @Tag(name = "Actors", description = "Policyholders, insurers, and experts")
  @GetMapping("/experts")
  @PreAuthorize("hasRole('claims-viewer')")
  public ResponseEntity<List<ActorDTO>> getExperts() {
    log.debug("Getting available experts");
    List<ActorDTO> experts = claimsService.getExperts();
    return ResponseEntity.ok(experts);
  }

  @Operation(summary = "Get policyholders list", operationId = "getPolicyholders")
  @ApiResponse(responseCode = "200", description = "List of policyholders")
  @Tag(name = "Actors", description = "Policyholders, insurers, and experts")
  @GetMapping("/policyholders")
  @PreAuthorize("hasRole('claims-viewer')")
  public ResponseEntity<List<ActorDTO>> getPolicyholders() {
    log.debug("Getting policyholders");
    List<ActorDTO> policyholders = claimsService.getPolicyholders();
    return ResponseEntity.ok(policyholders);
  }

  @Operation(summary = "Get insurers list", operationId = "getInsurers")
  @ApiResponse(responseCode = "200", description = "List of insurers")
  @Tag(name = "Actors", description = "Policyholders, insurers, and experts")
  @GetMapping("/insurers")
  @PreAuthorize("hasRole('claims-viewer')")
  public ResponseEntity<List<ActorDTO>> getInsurers() {
    log.debug("Getting insurers");
    List<ActorDTO> insurers = claimsService.getInsurers();
    return ResponseEntity.ok(insurers);
  }
}
