package com.ird0.directory.config.ssh;

import java.security.KeyPair;
import java.time.Duration;
import java.time.Instant;
import lombok.Builder;
import lombok.Getter;

/**
 * Represents a signed SSH certificate with its associated key pair and metadata.
 *
 * <p>This class encapsulates all the information needed for SSH certificate authentication:
 *
 * <ul>
 *   <li>The signed certificate (public key signed by Vault CA)
 *   <li>The ephemeral private key (for authentication)
 *   <li>Certificate metadata (serial, principal, validity period)
 * </ul>
 */
@Getter
@Builder
public class SignedCertificate {

  /** The signed public key certificate in OpenSSH format. */
  private final String signedPublicKey;

  /** The certificate serial number assigned by Vault. */
  private final String serial;

  /** The principal (username) this certificate authenticates. */
  private final String principal;

  /** When the certificate was issued. */
  private final Instant issuedAt;

  /** When the certificate expires. */
  private final Instant expiresAt;

  /** The ephemeral key pair (private key used for authentication). */
  private final KeyPair keyPair;

  /**
   * Checks if the certificate is expired.
   *
   * @return true if the certificate has expired
   */
  public boolean isExpired() {
    return Instant.now().isAfter(expiresAt);
  }

  /**
   * Checks if the certificate needs renewal (within renewal threshold).
   *
   * @param renewalThreshold how long before expiration to trigger renewal
   * @return true if renewal should be triggered
   */
  public boolean needsRenewal(Duration renewalThreshold) {
    Instant renewalTime = expiresAt.minus(renewalThreshold);
    return Instant.now().isAfter(renewalTime);
  }

  /**
   * Gets remaining validity duration.
   *
   * @return duration until certificate expires
   */
  public Duration getRemainingValidity() {
    Duration remaining = Duration.between(Instant.now(), expiresAt);
    return remaining.isNegative() ? Duration.ZERO : remaining;
  }
}
