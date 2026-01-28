package com.ird0.portal.exception;

import com.ird0.portal.client.IncidentClient.ServiceUnavailableException;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<Map<String, Object>> handleValidationExceptions(
      MethodArgumentNotValidException ex) {
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("timestamp", Instant.now());
    body.put("status", HttpStatus.BAD_REQUEST.value());
    body.put("error", "Validation Failed");

    Map<String, String> errors = new LinkedHashMap<>();
    ex.getBindingResult()
        .getAllErrors()
        .forEach(
            error -> {
              String fieldName = ((FieldError) error).getField();
              String errorMessage = error.getDefaultMessage();
              errors.put(fieldName, errorMessage);
            });
    body.put("errors", errors);

    return ResponseEntity.badRequest().body(body);
  }

  @ExceptionHandler(IllegalArgumentException.class)
  public ResponseEntity<Map<String, Object>> handleIllegalArgumentException(
      IllegalArgumentException ex) {
    log.warn("Illegal argument: {}", ex.getMessage());
    return buildErrorResponse(HttpStatus.BAD_REQUEST, ex.getMessage());
  }

  @ExceptionHandler(AccessDeniedException.class)
  public ResponseEntity<Map<String, Object>> handleAccessDeniedException(
      AccessDeniedException ex) {
    log.warn("Access denied: {}", ex.getMessage());
    return buildErrorResponse(
        HttpStatus.FORBIDDEN, "You do not have permission to perform this action");
  }

  @ExceptionHandler(ServiceUnavailableException.class)
  public ResponseEntity<Map<String, Object>> handleServiceUnavailableException(
      ServiceUnavailableException ex) {
    log.error("Service unavailable: {}", ex.getMessage());
    return buildErrorResponse(HttpStatus.SERVICE_UNAVAILABLE, ex.getMessage());
  }

  @ExceptionHandler(HttpClientErrorException.NotFound.class)
  public ResponseEntity<Map<String, Object>> handleNotFoundException(
      HttpClientErrorException.NotFound ex) {
    log.warn("Resource not found: {}", ex.getMessage());
    return buildErrorResponse(HttpStatus.NOT_FOUND, "Resource not found");
  }

  @ExceptionHandler(HttpClientErrorException.class)
  public ResponseEntity<Map<String, Object>> handleHttpClientErrorException(
      HttpClientErrorException ex) {
    log.warn("HTTP client error: {} - {}", ex.getStatusCode(), ex.getMessage());
    return buildErrorResponse(HttpStatus.valueOf(ex.getStatusCode().value()), ex.getStatusText());
  }

  @ExceptionHandler(ResourceAccessException.class)
  public ResponseEntity<Map<String, Object>> handleResourceAccessException(
      ResourceAccessException ex) {
    log.error("Resource access error: {}", ex.getMessage());
    return buildErrorResponse(
        HttpStatus.SERVICE_UNAVAILABLE, "Backend service is currently unavailable");
  }

  @ExceptionHandler(Exception.class)
  public ResponseEntity<Map<String, Object>> handleGenericException(Exception ex) {
    log.error("Unexpected error: ", ex);
    return buildErrorResponse(HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred");
  }

  private ResponseEntity<Map<String, Object>> buildErrorResponse(
      HttpStatus status, String message) {
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("timestamp", Instant.now());
    body.put("status", status.value());
    body.put("error", status.getReasonPhrase());
    body.put("message", message);
    return ResponseEntity.status(status).body(body);
  }
}
