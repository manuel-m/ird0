package com.ird0.sftp.config;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.PublicKey;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.sshd.common.config.keys.PublicKeyEntry;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.vault.core.VaultTemplate;
import org.springframework.vault.support.VaultResponse;

/**
 * Loads Vault SSH CA public key for certificate verification.
 *
 * <p>This CA public key is used as the trust anchor for validating client certificates signed by
 * Vault SSH Secrets Engine. The SFTP server uses this to verify that connecting clients present
 * certificates that were signed by the trusted CA.
 *
 * <p>The CA public key is loaded eagerly at startup via {@link PostConstruct}, ensuring fail-fast
 * behavior if Vault is misconfigured.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "vault.ssh.ca.enabled", havingValue = "true")
public class VaultCaTrustProvider {

  private static final String CA_PUBLIC_KEY_PATH = "ssh-client-signer/config/ca";

  private final VaultTemplate vaultTemplate;

  private PublicKey caPublicKey;

  /**
   * Initializes the CA public key by loading it from Vault.
   *
   * @throws IllegalStateException if the CA public key cannot be loaded
   */
  @PostConstruct
  public void init() {
    try {
      log.info("Loading Vault SSH CA public key from {}...", CA_PUBLIC_KEY_PATH);

      VaultResponse response = vaultTemplate.read(CA_PUBLIC_KEY_PATH);
      if (response == null) {
        throw new IllegalStateException("Vault SSH CA not configured at: " + CA_PUBLIC_KEY_PATH);
      }

      Map<String, Object> responseData = response.getData();
      if (responseData == null) {
        throw new IllegalStateException(
            "Vault SSH CA response has no data at: " + CA_PUBLIC_KEY_PATH);
      }

      String publicKeyData = (String) responseData.get("public_key");
      if (publicKeyData == null || publicKeyData.isEmpty()) {
        throw new IllegalStateException("Vault SSH CA public_key is empty");
      }

      PublicKey parsedKey = parsePublicKey(publicKeyData);
      if (parsedKey == null) {
        throw new IllegalStateException(
            "Failed to parse Vault SSH CA public key: parser returned null");
      }

      this.caPublicKey = parsedKey;
      log.info("Vault SSH CA public key loaded successfully");
    } catch (IOException | GeneralSecurityException e) {
      log.error("Failed to load Vault SSH CA public key: {}", e.getMessage(), e);
      throw new IllegalStateException("Failed to load Vault SSH CA public key", e);
    }
  }

  /**
   * Returns the Vault SSH CA public key.
   *
   * @return the CA public key used for certificate verification
   * @throws IllegalStateException if called before initialization completes
   */
  public PublicKey getCaPublicKey() {
    if (caPublicKey == null) {
      throw new IllegalStateException("CA public key not initialized - init() may have failed");
    }
    return caPublicKey;
  }

  private PublicKey parsePublicKey(String publicKeyData)
      throws IOException, GeneralSecurityException {
    // Parse OpenSSH format public key (e.g., "ssh-rsa AAAA... comment")
    String trimmed = publicKeyData.trim();
    PublicKeyEntry entry = PublicKeyEntry.parsePublicKeyEntry(trimmed);
    if (entry == null) {
      throw new IOException("Failed to parse public key entry from: " + trimmed);
    }
    return entry.resolvePublicKey(null, null, null);
  }
}
