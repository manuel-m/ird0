package com.ird0.directory.config.ssh;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.security.KeyPair;
import java.security.NoSuchAlgorithmException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantLock;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Manages SSH certificate lifecycle including generation, renewal, and caching.
 *
 * <p>This component provides thread-safe access to the current valid SSH certificate. It
 * automatically renews certificates before they expire and ensures that no certificate material is
 * ever written to disk.
 *
 * <p>Key features:
 *
 * <ul>
 *   <li>Generates ephemeral key pairs in memory
 *   <li>Requests certificate signing from Vault
 *   <li>Caches the current certificate
 *   <li>Auto-renews before expiration (scheduled task every 60s)
 *   <li>Thread-safe renewal with lock
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "directory.sftp-import.enabled", havingValue = "true")
public class SshCertificateManager {

  private final EphemeralKeyPairGenerator keyPairGenerator;
  private final VaultSshCertificateSigner certificateSigner;
  private final SshCertificateProperties properties;

  private final AtomicReference<SignedCertificate> currentCertificate = new AtomicReference<>();
  private final ReentrantLock renewalLock = new ReentrantLock();
  private volatile boolean initialized = false;

  @PostConstruct
  public void init() {
    log.info(
        "SSH Certificate Manager ready with TTL={}, renewalThreshold={}",
        properties.getTtl(),
        properties.getRenewalThreshold());
    log.info("Certificate will be obtained on first use");
  }

  /**
   * Gets the current valid certificate, triggering renewal if needed.
   *
   * <p>Thread-safe method that returns the cached certificate. If the certificate is approaching
   * expiration, triggers an asynchronous renewal.
   *
   * @return the current valid SignedCertificate
   * @throws IllegalStateException if no valid certificate is available
   */
  public SignedCertificate getCurrentCertificate() {
    SignedCertificate cert = currentCertificate.get();

    // Lazy initialization: obtain first certificate on first call
    if (!initialized && (cert == null || cert.isExpired())) {
      renewalLock.lock();
      try {
        // Double-check after acquiring lock
        cert = currentCertificate.get();
        if (!initialized && (cert == null || cert.isExpired())) {
          log.info("Obtaining initial SSH certificate on first use...");
          obtainNewCertificate();
          initialized = true;
          cert = currentCertificate.get();
        }
      } catch (Exception e) {
        log.error("Failed to obtain initial certificate: {}", e.getMessage(), e);
        throw new IllegalStateException(
            "Failed to obtain SSH certificate from Vault. "
                + "Ensure Vault is running and SSH Secrets Engine is configured.",
            e);
      } finally {
        renewalLock.unlock();
      }
    }

    if (cert == null || cert.isExpired()) {
      throw new IllegalStateException("No valid SSH certificate available");
    }

    if (cert.needsRenewal(properties.getRenewalThreshold())) {
      triggerAsyncRenewal();
    }

    return cert;
  }

  /**
   * Checks if a valid certificate is available.
   *
   * @return true if a non-expired certificate is available
   */
  public boolean hasCertificate() {
    SignedCertificate cert = currentCertificate.get();
    return cert != null && !cert.isExpired();
  }

  /**
   * Scheduled task to check and renew certificates proactively.
   *
   * <p>Runs every 60 seconds to ensure certificates are renewed before expiration. This provides a
   * safety net in case on-demand renewal fails.
   */
  @Scheduled(fixedRate = 60000) // Every minute
  public void checkAndRenew() {
    SignedCertificate cert = currentCertificate.get();
    if (cert != null && cert.needsRenewal(properties.getRenewalThreshold())) {
      try {
        obtainNewCertificate();
      } catch (Exception e) {
        log.error("Scheduled certificate renewal failed: {}", e.getMessage(), e);
      }
    }
  }

  private void triggerAsyncRenewal() {
    if (renewalLock.tryLock()) {
      try {
        SignedCertificate cert = currentCertificate.get();
        if (cert != null && cert.needsRenewal(properties.getRenewalThreshold())) {
          obtainNewCertificate();
        }
      } catch (Exception e) {
        log.error("Certificate renewal failed: {}", e.getMessage(), e);
      } finally {
        renewalLock.unlock();
      }
    }
  }

  private void obtainNewCertificate() throws NoSuchAlgorithmException, IOException {
    renewalLock.lock();
    try {
      log.info("Obtaining new SSH certificate...");

      // Generate new ephemeral key pair
      KeyPair keyPair = keyPairGenerator.generateKeyPair();

      // Sign the public key with Vault
      SignedCertificate newCert = certificateSigner.signPublicKey(keyPair);

      SignedCertificate oldCert = currentCertificate.getAndSet(newCert);

      if (oldCert != null) {
        log.info(
            "Certificate renewed. oldSerial={}, newSerial={}, expiresAt={}",
            oldCert.getSerial(),
            newCert.getSerial(),
            newCert.getExpiresAt());
      } else {
        log.info(
            "Initial certificate obtained. serial={}, expiresAt={}",
            newCert.getSerial(),
            newCert.getExpiresAt());
      }
    } finally {
      renewalLock.unlock();
    }
  }

  @PreDestroy
  public void shutdown() {
    log.info("SSH Certificate Manager shutting down");
    currentCertificate.set(null);
  }
}
