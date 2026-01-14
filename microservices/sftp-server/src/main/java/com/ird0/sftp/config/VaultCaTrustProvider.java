package com.ird0.sftp.config;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.PublicKey;
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
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "vault.ssh.ca.enabled", havingValue = "true")
public class VaultCaTrustProvider {

  private static final String CA_PUBLIC_KEY_PATH = "ssh-client-signer/config/ca";

  private final VaultTemplate vaultTemplate;

  private PublicKey caPublicKey;
  private boolean initialized = false;
  private final Object lock = new Object();

  public PublicKey getCaPublicKey() {
    if (!initialized) {
      synchronized (lock) {
        if (!initialized) {
          loadCaPublicKey();
        }
      }
    }
    return caPublicKey;
  }

  private void loadCaPublicKey() {
    try {
      log.info("Loading Vault SSH CA public key from {}...", CA_PUBLIC_KEY_PATH);

      VaultResponse response = vaultTemplate.read(CA_PUBLIC_KEY_PATH);
      if (response == null || response.getData() == null) {
        throw new IllegalStateException("Vault SSH CA not configured at: " + CA_PUBLIC_KEY_PATH);
      }

      String publicKeyData = (String) response.getData().get("public_key");
      if (publicKeyData == null || publicKeyData.isEmpty()) {
        throw new IllegalStateException("Vault SSH CA public_key is empty");
      }

      this.caPublicKey = parsePublicKey(publicKeyData);
      this.initialized = true;
      log.info("Vault SSH CA public key loaded successfully");
    } catch (IOException | GeneralSecurityException e) {
      log.error("Failed to load Vault SSH CA public key: {}", e.getMessage(), e);
      throw new IllegalStateException("Failed to load Vault SSH CA public key", e);
    }
  }

  private PublicKey parsePublicKey(String publicKeyData)
      throws IOException, GeneralSecurityException {
    // Parse OpenSSH format public key (e.g., "ssh-rsa AAAA... comment")
    String trimmed = publicKeyData.trim();
    PublicKeyEntry entry = PublicKeyEntry.parsePublicKeyEntry(trimmed);
    return entry.resolvePublicKey(null, null, null);
  }
}
