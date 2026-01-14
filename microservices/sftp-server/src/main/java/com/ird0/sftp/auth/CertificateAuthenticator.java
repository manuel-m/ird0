package com.ird0.sftp.auth;

import com.ird0.sftp.config.SftpProperties;
import com.ird0.sftp.config.VaultCaTrustProvider;
import java.security.PublicKey;
import java.time.Instant;
import java.util.Collection;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.sshd.common.config.keys.OpenSshCertificate;
import org.apache.sshd.server.auth.AsyncAuthException;
import org.apache.sshd.server.auth.pubkey.PublickeyAuthenticator;
import org.apache.sshd.server.session.ServerSession;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Authenticates SSH clients using certificates signed by Vault CA.
 *
 * <p>This authenticator replaces static public key authentication with certificate-based
 * authentication. Certificates must be signed by the trusted Vault CA and meet the following
 * criteria:
 *
 * <ol>
 *   <li>Key must be an OpenSSH certificate (not a raw public key)
 *   <li>Certificate must be signed by the trusted Vault CA
 *   <li>Certificate must not be expired
 *   <li>Username must match one of the certificate principals
 * </ol>
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "vault.ssh.ca.enabled", havingValue = "true")
public class CertificateAuthenticator implements PublickeyAuthenticator {

  private final VaultCaTrustProvider caTrustProvider;
  private final CertificateAuditLogger auditLogger;
  private final SftpProperties properties;

  @Override
  public boolean authenticate(String username, PublicKey key, ServerSession session)
      throws AsyncAuthException {

    String sessionId = extractSessionId(session);
    String clientAddress = extractClientAddress(session);

    // Step 1: Verify this is an OpenSSH certificate, not a raw public key
    if (!(key instanceof OpenSshCertificate)) {
      log.warn(
          "Authentication rejected: raw public key not allowed. "
              + "sessionId={}, username={}, clientAddress={}",
          sessionId,
          username,
          clientAddress);
      auditLogger.logRejection(username, clientAddress, "RAW_KEY_NOT_ALLOWED");
      return false;
    }

    OpenSshCertificate certificate = (OpenSshCertificate) key;

    // Step 2: Verify certificate signature against trusted CA
    if (!verifyCaSignature(certificate)) {
      log.warn(
          "Authentication rejected: certificate not signed by trusted CA. "
              + "sessionId={}, username={}, clientAddress={}",
          sessionId,
          username,
          clientAddress);
      auditLogger.logRejection(username, clientAddress, "INVALID_CA_SIGNATURE");
      return false;
    }

    // Step 3: Verify certificate validity period
    if (!verifyCertificateValidity(certificate)) {
      log.warn(
          "Authentication rejected: certificate expired or not yet valid. "
              + "sessionId={}, username={}, clientAddress={}, validAfter={}, validBefore={}",
          sessionId,
          username,
          clientAddress,
          certificate.getValidAfter(),
          certificate.getValidBefore());
      auditLogger.logRejection(username, clientAddress, "CERTIFICATE_EXPIRED");
      return false;
    }

    // Step 4: Verify username matches certificate principals
    if (!verifyPrincipal(certificate, username)) {
      log.warn(
          "Authentication rejected: username not in certificate principals. "
              + "sessionId={}, username={}, clientAddress={}, principals={}",
          sessionId,
          username,
          clientAddress,
          certificate.getPrincipals());
      auditLogger.logRejection(username, clientAddress, "PRINCIPAL_MISMATCH");
      return false;
    }

    // Step 5: Verify certificate type is user (not host)
    if (!verifyCertificateType(certificate)) {
      log.warn(
          "Authentication rejected: invalid certificate type. "
              + "sessionId={}, username={}, clientAddress={}, certType={}",
          sessionId,
          username,
          clientAddress,
          certificate.getType());
      auditLogger.logRejection(username, clientAddress, "INVALID_CERT_TYPE");
      return false;
    }

    // Authentication successful
    String certSerial = String.valueOf(certificate.getSerial());
    Instant validUntil = Instant.ofEpochSecond(certificate.getValidBefore());

    log.info(
        "Certificate authentication successful. "
            + "sessionId={}, username={}, clientAddress={}, certificateSerial={}, validUntil={}",
        sessionId,
        username,
        clientAddress,
        certSerial,
        validUntil);

    auditLogger.logSuccess(username, clientAddress, certSerial, validUntil);

    return true;
  }

  private boolean verifyCaSignature(OpenSshCertificate certificate) {
    try {
      PublicKey caPublicKey = caTrustProvider.getCaPublicKey();
      PublicKey certCaKey = certificate.getCaPubKey();

      // Verify the CA public key in certificate matches our trusted CA
      return caPublicKey.equals(certCaKey);
    } catch (Exception e) {
      log.error("Error verifying CA signature: {}", e.getMessage(), e);
      return false;
    }
  }

  private boolean verifyCertificateValidity(OpenSshCertificate certificate) {
    Instant now = Instant.now();
    Instant validAfter = Instant.ofEpochSecond(certificate.getValidAfter());
    Instant validBefore = Instant.ofEpochSecond(certificate.getValidBefore());

    // Check if current time is within validity window
    return !now.isBefore(validAfter) && now.isBefore(validBefore);
  }

  private boolean verifyPrincipal(OpenSshCertificate certificate, String username) {
    Collection<String> principals = certificate.getPrincipals();
    return principals != null && principals.contains(username);
  }

  private boolean verifyCertificateType(OpenSshCertificate certificate) {
    // Type 1 = user certificate, Type 2 = host certificate
    // We only accept user certificates
    return certificate.getType() == OpenSshCertificate.Type.USER;
  }

  private String extractSessionId(ServerSession session) {
    if (session.getSessionId() != null) {
      byte[] id = session.getSessionId();
      // Return first 8 bytes as hex for brevity
      StringBuilder sb = new StringBuilder();
      for (int i = 0; i < Math.min(8, id.length); i++) {
        sb.append(String.format("%02x", id[i]));
      }
      return sb.toString();
    }
    return "unknown";
  }

  private String extractClientAddress(ServerSession session) {
    if (session.getClientAddress() != null) {
      return session.getClientAddress().toString();
    }
    return "unknown";
  }
}
