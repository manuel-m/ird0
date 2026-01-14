package com.ird0.directory.config.ssh;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Generates ephemeral RSA key pairs for SSH certificate signing.
 *
 * <p>Keys are generated in memory and never persisted to disk. Each certificate request uses a
 * freshly generated key pair, providing forward secrecy and limiting the impact of key compromise.
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "directory.sftp-import.enabled", havingValue = "true")
public class EphemeralKeyPairGenerator {

  private static final String ALGORITHM = "RSA";
  private static final int KEY_SIZE = 4096;

  private final SecureRandom secureRandom;

  public EphemeralKeyPairGenerator() {
    this.secureRandom = new SecureRandom();
  }

  /**
   * Generates a new RSA key pair in memory.
   *
   * @return newly generated KeyPair with RSA-4096 keys
   * @throws NoSuchAlgorithmException if RSA algorithm is not available
   */
  public KeyPair generateKeyPair() throws NoSuchAlgorithmException {
    KeyPairGenerator keyGen = KeyPairGenerator.getInstance(ALGORITHM);
    keyGen.initialize(KEY_SIZE, secureRandom);
    KeyPair keyPair = keyGen.generateKeyPair();
    log.debug("Generated ephemeral RSA-{} key pair", KEY_SIZE);
    return keyPair;
  }
}
