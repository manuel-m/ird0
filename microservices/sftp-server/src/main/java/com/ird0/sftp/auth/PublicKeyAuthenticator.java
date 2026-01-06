package com.ird0.sftp.auth;

import com.ird0.sftp.config.SftpProperties;
import jakarta.annotation.PostConstruct;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.PublicKey;
import java.util.HashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.sshd.common.config.keys.PublicKeyEntry;
import org.apache.sshd.server.auth.AsyncAuthException;
import org.apache.sshd.server.auth.pubkey.PublickeyAuthenticator;
import org.apache.sshd.server.session.ServerSession;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class PublicKeyAuthenticator implements PublickeyAuthenticator {

  private final SftpProperties properties;
  private final Map<String, PublicKey> authorizedKeys = new HashMap<>();

  @PostConstruct
  public void init() throws Exception {
    Path keysFile = Paths.get(properties.getServer().getAuthorizedKeysPath());

    // Critical: File must exist
    if (!Files.exists(keysFile)) {
      throw new IllegalStateException(
          "Authorized keys file not found: " + keysFile.toAbsolutePath());
    }

    // Critical: File must be readable
    if (!Files.isReadable(keysFile)) {
      throw new IllegalStateException(
          "Authorized keys file is not readable: " + keysFile.toAbsolutePath());
    }

    // Parse file line by line manually due to Apache SSHD parser limitations
    int lineNumber = 0;
    int validEntries = 0;

    for (String line : Files.readAllLines(keysFile)) {
      lineNumber++;

      // Skip empty lines and comments
      String trimmed = line.trim();
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

        // Parse using PublicKeyEntry (more reliable than AuthorizedKeyEntry)
        String keyEntry = keyType + " " + keyData;
        PublicKeyEntry entry = PublicKeyEntry.parsePublicKeyEntry(keyEntry);
        PublicKey publicKey = entry.resolvePublicKey(null, null, null);

        // Check for duplicates
        if (authorizedKeys.containsKey(username)) {
          log.warn(
              "Line {}: Duplicate username '{}', overwriting previous entry", lineNumber, username);
        }

        // Store key
        authorizedKeys.put(username, publicKey);
        validEntries++;
        log.info("Loaded authorized key for user: {}", username);

      } catch (Exception e) {
        log.error("Line {}: Failed to parse authorized key: {}", lineNumber, e.getMessage());
      }
    }

    // Critical: At least one valid entry required
    if (validEntries == 0) {
      throw new IllegalStateException(
          "No valid authorized keys found in: " + keysFile.toAbsolutePath());
    }

    log.info("Loaded {} authorized keys from {}", validEntries, keysFile.toAbsolutePath());
  }

  @Override
  public boolean authenticate(String username, PublicKey key, ServerSession session)
      throws AsyncAuthException {

    PublicKey authorizedKey = authorizedKeys.get(username);

    if (authorizedKey == null) {
      log.warn("No authorized key found for user: {}", username);
      return false;
    }

    boolean authenticated = authorizedKey.equals(key);

    if (authenticated) {
      log.info("User {} authenticated successfully", username);
    } else {
      log.warn("Authentication failed for user: {}", username);
    }

    return authenticated;
  }
}
