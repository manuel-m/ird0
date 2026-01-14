package com.ird0.sftp.auth;

import java.time.Instant;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Audit logger for SSH certificate authentication events.
 *
 * <p>Provides structured logging for security monitoring and compliance. All authentication events
 * are logged with a consistent format to enable log aggregation and alerting.
 */
@Slf4j
@Component
public class CertificateAuditLogger {

  private static final String AUDIT_MARKER = "[AUDIT]";

  /**
   * Logs successful certificate authentication.
   *
   * @param username the authenticated username
   * @param clientAddress the client's IP address
   * @param certificateSerial the certificate serial number
   * @param validUntil when the certificate expires
   */
  public void logSuccess(
      String username, String clientAddress, String certificateSerial, Instant validUntil) {
    log.info(
        "{} AUTH_SUCCESS username={} clientAddress={} certificateSerial={} validUntil={}",
        AUDIT_MARKER,
        username,
        clientAddress,
        certificateSerial,
        validUntil);
  }

  /**
   * Logs rejected authentication attempt.
   *
   * @param username the attempted username
   * @param clientAddress the client's IP address
   * @param reason the rejection reason code
   */
  public void logRejection(String username, String clientAddress, String reason) {
    log.warn(
        "{} AUTH_REJECTED username={} clientAddress={} reason={}",
        AUDIT_MARKER,
        username,
        clientAddress,
        reason);
  }

  /**
   * Logs certificate renewal event.
   *
   * @param username the username associated with the certificate
   * @param oldCertSerial the old certificate serial
   * @param newCertSerial the new certificate serial
   * @param newValidUntil when the new certificate expires
   */
  public void logCertificateRenewal(
      String username, String oldCertSerial, String newCertSerial, Instant newValidUntil) {
    log.info(
        "{} CERT_RENEWED username={} oldSerial={} newSerial={} newValidUntil={}",
        AUDIT_MARKER,
        username,
        oldCertSerial,
        newCertSerial,
        newValidUntil);
  }
}
