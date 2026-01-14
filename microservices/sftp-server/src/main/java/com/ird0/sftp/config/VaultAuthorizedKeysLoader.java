package com.ird0.sftp.config;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.PublicKey;
import java.util.HashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.sshd.common.config.keys.PublicKeyEntry;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.vault.core.VaultTemplate;
import org.springframework.vault.support.VaultResponse;

/**
 * Loads SSH authorized keys from HashiCorp Vault.
 *
 * <p>This component provides authorized public keys for SFTP authentication from Vault, enabling
 * centralized key management.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "vault.enabled", havingValue = "true", matchIfMissing = false)
public class VaultAuthorizedKeysLoader {

  private static final String VAULT_PATH = "secret/data/ird0/sftp/authorized-keys";

  private final VaultTemplate vaultTemplate;

  /**
   * Loads authorized keys from Vault and returns a map of username to public key.
   *
   * @return Map of username to public key
   * @throws IOException if key parsing fails
   */
  public Map<String, PublicKey> loadAuthorizedKeys() throws IOException {
    log.info("Loading authorized keys from Vault...");

    VaultResponse response = vaultTemplate.read(VAULT_PATH);
    if (response == null || response.getData() == null) {
      log.warn("No authorized keys found in Vault at: {}", VAULT_PATH);
      return new HashMap<>();
    }

    @SuppressWarnings("unchecked")
    Map<String, Object> data = (Map<String, Object>) response.getData().get("data");
    if (data == null) {
      log.warn("Authorized keys data is null in Vault");
      return new HashMap<>();
    }

    String content = (String) data.get("content");
    if (content == null || content.isEmpty()) {
      log.warn("Authorized keys content is empty in Vault");
      return new HashMap<>();
    }

    return parseAuthorizedKeys(content);
  }

  private Map<String, PublicKey> parseAuthorizedKeys(String content) throws IOException {
    Map<String, PublicKey> authorizedKeys = new HashMap<>();
    int lineNumber = 0;

    for (String line : content.split("\n")) {
      lineNumber++;
      String trimmed = line.trim();

      // Skip empty lines and comments
      if (trimmed.isEmpty() || trimmed.startsWith("#")) {
        continue;
      }

      try {
        // Split the line into parts: key-type, key-data, username
        String[] parts = trimmed.split("\\s+");
        if (parts.length < 3) {
          log.warn(
              "Line {}: Invalid format (expected: <key-type> <key-data> <username>), skipping",
              lineNumber);
          continue;
        }

        String keyType = parts[0];
        String keyData = parts[1];
        String username = parts[2];

        // Parse using PublicKeyEntry
        String keyEntry = keyType + " " + keyData;
        PublicKeyEntry entry = PublicKeyEntry.parsePublicKeyEntry(keyEntry);
        PublicKey publicKey = entry.resolvePublicKey(null, null, null);

        authorizedKeys.put(username, publicKey);
        log.info("Loaded authorized key for user: {}", username);

      } catch (IOException | GeneralSecurityException | IllegalArgumentException e) {
        log.error("Line {}: Failed to parse authorized key: {}", lineNumber, e.getMessage());
      }
    }

    log.info("Loaded {} authorized keys from Vault", authorizedKeys.size());
    return authorizedKeys;
  }
}
