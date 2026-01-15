package com.ird0.incident.model;

public enum IncidentStatus {
  DECLARED,
  UNDER_REVIEW,
  QUALIFIED,
  ABANDONED,
  IN_PROGRESS,
  CLOSED;

  public boolean canTransitionTo(IncidentStatus target) {
    return switch (this) {
      case DECLARED -> target == UNDER_REVIEW;
      case UNDER_REVIEW -> target == QUALIFIED || target == ABANDONED;
      case QUALIFIED -> target == IN_PROGRESS || target == ABANDONED;
      case IN_PROGRESS -> target == CLOSED;
      case ABANDONED, CLOSED -> false;
    };
  }
}
