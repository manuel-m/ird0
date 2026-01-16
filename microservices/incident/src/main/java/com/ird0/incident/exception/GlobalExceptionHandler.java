package com.ird0.incident.exception;

import com.ird0.commons.exception.ErrorResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

@ControllerAdvice
public class GlobalExceptionHandler {

  @ExceptionHandler(IncidentNotFoundException.class)
  public ResponseEntity<ErrorResponse> handleNotFound(IncidentNotFoundException ex) {
    return ResponseEntity.status(HttpStatus.NOT_FOUND)
        .body(new ErrorResponse(404, ex.getMessage()));
  }

  @ExceptionHandler(InvalidStateTransitionException.class)
  public ResponseEntity<StateTransitionErrorResponse> handleInvalidTransition(
      InvalidStateTransitionException ex) {
    return ResponseEntity.status(HttpStatus.CONFLICT)
        .body(
            new StateTransitionErrorResponse(
                409, ex.getMessage(), ex.getCurrentStatus(), ex.getRequestedStatus()));
  }

  @ExceptionHandler(DirectoryValidationException.class)
  public ResponseEntity<ErrorResponse> handleDirectoryValidation(DirectoryValidationException ex) {
    return ResponseEntity.status(HttpStatus.BAD_REQUEST)
        .body(new ErrorResponse(400, ex.getMessage()));
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex) {
    String message =
        ex.getBindingResult().getFieldErrors().stream()
            .map(error -> error.getField() + ": " + error.getDefaultMessage())
            .reduce((a, b) -> a + "; " + b)
            .orElse("Validation failed");
    return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ErrorResponse(400, message));
  }

  @ExceptionHandler(Exception.class)
  public ResponseEntity<ErrorResponse> handleGeneric(Exception ex) {
    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
        .body(new ErrorResponse(500, "Internal server error: " + ex.getMessage()));
  }
}
