package com.ird0.sftp.config;

import jakarta.annotation.PostConstruct;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.util.Collections;
import java.util.Iterator;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.sshd.common.keyprovider.KeyPairProvider;
import org.apache.sshd.common.session.SessionContext;
import org.apache.sshd.common.util.security.SecurityUtils;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.vault.core.VaultTemplate;
import org.springframework.vault.support.VaultResponse;

/**
 * Provides SSH host key from HashiCorp Vault.
 *
 * <p>This component reads the SFTP server's RSA host key from Vault at startup, eliminating the
 * need to persist the key on the filesystem. This solves the issue of host key regeneration on
 * container restarts.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "vault.enabled", havingValue = "true", matchIfMissing = false)
public class VaultHostKeyProvider implements KeyPairProvider {

  private static final String VAULT_PATH = "secret/data/ird0/sftp/host-key";

  private final VaultTemplate vaultTemplate;
  private KeyPair hostKeyPair;

  @PostConstruct
  public void init() throws IOException, GeneralSecurityException {
    log.info("Loading SFTP host key from Vault...");

    VaultResponse response = vaultTemplate.read(VAULT_PATH);
    if (response == null) {
      throw new IllegalStateException("SFTP host key not found in Vault at: " + VAULT_PATH);
    }

    Map<String, Object> responseData = response.getData();
    if (responseData == null) {
      throw new IllegalStateException("SFTP host key response has no data at: " + VAULT_PATH);
    }

    @SuppressWarnings("unchecked")
    Map<String, Object> data = (Map<String, Object>) responseData.get("data");
    if (data == null) {
      throw new IllegalStateException("SFTP host key data is null in Vault at: " + VAULT_PATH);
    }

    String privateKeyPem = (String) data.get("private_key");
    if (privateKeyPem == null || privateKeyPem.isEmpty()) {
      throw new IllegalStateException("SFTP host key private_key is empty in Vault");
    }

    this.hostKeyPair = loadKeyPairFromPem(privateKeyPem);
    log.info("SFTP host key loaded successfully from Vault");
  }

  private KeyPair loadKeyPairFromPem(String pemContent)
      throws IOException, GeneralSecurityException {
    try (InputStream inputStream =
        new ByteArrayInputStream(pemContent.getBytes(StandardCharsets.UTF_8))) {
      Iterable<KeyPair> keyPairs =
          SecurityUtils.loadKeyPairIdentities(null, null, inputStream, null);
      if (keyPairs == null) {
        throw new IOException("Key pair loader returned null for PEM content");
      }
      Iterator<KeyPair> iterator = keyPairs.iterator();
      if (!iterator.hasNext()) {
        throw new IOException("No key pair found in PEM content");
      }
      KeyPair keyPair = iterator.next();
      if (keyPair == null) {
        throw new IOException("Key pair iterator returned null key pair");
      }
      return keyPair;
    }
  }

  @Override
  public Iterable<KeyPair> loadKeys(SessionContext session) {
    if (hostKeyPair == null) {
      log.error("Host key pair is null - Vault initialization may have failed");
      return Collections.emptyList();
    }
    return Collections.singleton(hostKeyPair);
  }
}
