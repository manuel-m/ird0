package com.ird0.sftp.auth;

import com.ird0.sftp.config.SftpProperties;
import jakarta.annotation.PostConstruct;
import java.security.PublicKey;
import java.util.HashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.sshd.common.config.keys.AuthorizedKeyEntry;
import org.apache.sshd.common.config.keys.PublicKeyEntryResolver;
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
    // Load all authorized keys from configuration
    for (SftpProperties.UserConfig user : properties.getUsers()) {
      PublicKey publicKey = parsePublicKey(user.getPublicKey());
      authorizedKeys.put(user.getUsername(), publicKey);
      log.info("Loaded authorized key for user: {}", user.getUsername());
    }
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

  private PublicKey parsePublicKey(String keyString) throws Exception {
    AuthorizedKeyEntry entry = AuthorizedKeyEntry.parseAuthorizedKeyEntry(keyString);
    return entry.resolvePublicKey(null, PublicKeyEntryResolver.FAILING);
  }
}
