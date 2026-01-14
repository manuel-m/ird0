package com.ird0.directory.config;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;
import org.springframework.vault.core.VaultTemplate;
import org.springframework.vault.support.VaultResponse;

/**
 * Loads SFTP client key and known hosts from HashiCorp Vault.
 *
 * <p>This component retrieves SSH keys from Vault and provides them as resources for the SFTP
 * client configuration. Keys are written to temporary files to comply with the Spring Integration
 * SFTP API which expects file-based resources.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "vault.enabled", havingValue = "true", matchIfMissing = false)
public class VaultSftpKeyLoader {

  private static final String CLIENT_KEY_PATH = "secret/data/ird0/sftp/client-key";
  private static final String KNOWN_HOSTS_PATH = "secret/data/ird0/sftp/known-hosts";

  private final VaultTemplate vaultTemplate;

  private File clientKeyFile;
  private File knownHostsFile;

  /**
   * Gets the SFTP client private key as a Resource.
   *
   * @return Resource pointing to the client private key file
   * @throws IOException if key cannot be loaded from Vault
   */
  public Resource getClientKeyResource() throws IOException {
    if (clientKeyFile == null || !clientKeyFile.exists()) {
      clientKeyFile = loadKeyToTempFile(CLIENT_KEY_PATH, "private_key", "sftp-client-key", ".pem");
    }
    return new FileSystemResource(clientKeyFile);
  }

  /**
   * Gets the known hosts file as a Resource.
   *
   * @return Resource pointing to the known hosts file
   * @throws IOException if known hosts cannot be loaded from Vault
   */
  public Resource getKnownHostsResource() throws IOException {
    if (knownHostsFile == null || !knownHostsFile.exists()) {
      knownHostsFile = loadKeyToTempFile(KNOWN_HOSTS_PATH, "content", "sftp-known-hosts", "");
    }
    return new FileSystemResource(knownHostsFile);
  }

  /**
   * Checks if the client key is available in Vault.
   *
   * @return true if client key exists in Vault
   */
  public boolean hasClientKey() {
    try {
      VaultResponse response = vaultTemplate.read(CLIENT_KEY_PATH);
      return response != null && response.getData() != null;
    } catch (Exception e) {
      log.debug("Client key not available in Vault: {}", e.getMessage());
      return false;
    }
  }

  /**
   * Checks if the known hosts file is available in Vault.
   *
   * @return true if known hosts exists in Vault
   */
  public boolean hasKnownHosts() {
    try {
      VaultResponse response = vaultTemplate.read(KNOWN_HOSTS_PATH);
      return response != null && response.getData() != null;
    } catch (Exception e) {
      log.debug("Known hosts not available in Vault: {}", e.getMessage());
      return false;
    }
  }

  private File loadKeyToTempFile(String vaultPath, String dataKey, String prefix, String suffix)
      throws IOException {
    log.info("Loading {} from Vault...", vaultPath);

    VaultResponse response = vaultTemplate.read(vaultPath);
    if (response == null || response.getData() == null) {
      throw new IOException("Secret not found in Vault at: " + vaultPath);
    }

    @SuppressWarnings("unchecked")
    Map<String, Object> data = (Map<String, Object>) response.getData().get("data");
    if (data == null) {
      throw new IOException("Secret data is null in Vault at: " + vaultPath);
    }

    String content = (String) data.get(dataKey);
    if (content == null || content.isEmpty()) {
      throw new IOException("Secret " + dataKey + " is empty in Vault at: " + vaultPath);
    }

    // Create temporary file
    Path tempFile = Files.createTempFile(prefix, suffix);
    File file = tempFile.toFile();
    file.deleteOnExit();

    // Write content to file
    try (FileWriter writer = new FileWriter(file)) {
      writer.write(content);
    }

    // Set restrictive permissions
    file.setReadable(false, false);
    file.setReadable(true, true);
    file.setWritable(false, false);
    file.setWritable(true, true);

    log.info("Loaded {} from Vault to temporary file", vaultPath);
    return file;
  }
}
