package com.ird0.directory.config.ssh;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.security.KeyPair;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.vault.core.VaultTemplate;
import org.springframework.vault.support.VaultResponse;

/**
 * Signs SSH public keys using Vault SSH Secrets Engine.
 *
 * <p>This component calls the Vault SSH CA to sign public keys and return short-lived certificates.
 * The certificates can then be used for SFTP authentication.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "directory.sftp-import.enabled", havingValue = "true")
public class VaultSshCertificateSigner {

  private static final String SIGN_PATH_TEMPLATE = "ssh-client-signer/sign/%s";

  private final VaultTemplate vaultTemplate;
  private final SshCertificateProperties properties;

  /**
   * Signs an SSH public key and returns a SignedCertificate.
   *
   * @param keyPair the key pair containing the public key to sign
   * @return SignedCertificate containing the signed certificate and metadata
   * @throws IOException if signing fails
   */
  public SignedCertificate signPublicKey(KeyPair keyPair) throws IOException {
    String publicKeyOpenSsh = formatPublicKeyOpenSsh(keyPair);
    String signPath = String.format(SIGN_PATH_TEMPLATE, properties.getVaultRole());

    Map<String, Object> request = new HashMap<>();
    request.put("public_key", publicKeyOpenSsh);
    request.put("valid_principals", properties.getPrincipal());
    request.put("ttl", properties.getTtl().toSeconds() + "s");
    request.put("cert_type", "user");

    log.info(
        "Requesting SSH certificate from Vault for principal: {}, TTL: {}",
        properties.getPrincipal(),
        properties.getTtl());

    VaultResponse response = vaultTemplate.write(signPath, request);
    if (response == null || response.getData() == null) {
      throw new IOException("Failed to sign public key: no response from Vault at " + signPath);
    }

    String signedKey = (String) response.getData().get("signed_key");
    String serial = (String) response.getData().get("serial_number");

    if (signedKey == null || signedKey.isEmpty()) {
      throw new IOException("Failed to sign public key: empty signed_key in response");
    }

    Instant issuedAt = Instant.now();
    Instant expiresAt = issuedAt.plus(properties.getTtl());

    log.info("SSH certificate obtained. serial={}, expiresAt={}", serial, expiresAt);

    return SignedCertificate.builder()
        .signedPublicKey(signedKey)
        .serial(serial)
        .principal(properties.getPrincipal())
        .issuedAt(issuedAt)
        .expiresAt(expiresAt)
        .keyPair(keyPair)
        .build();
  }

  /**
   * Formats an RSA public key in OpenSSH format.
   *
   * @param keyPair the key pair containing the RSA public key
   * @return OpenSSH formatted public key string (e.g., "ssh-rsa AAAA...")
   */
  private String formatPublicKeyOpenSsh(KeyPair keyPair) throws IOException {
    RSAPublicKey rsaPublicKey = (RSAPublicKey) keyPair.getPublic();
    byte[] encoded = encodeRsaPublicKey(rsaPublicKey);
    String base64 = Base64.getEncoder().encodeToString(encoded);
    return "ssh-rsa " + base64;
  }

  /**
   * Encodes an RSA public key in the OpenSSH wire format.
   *
   * <p>Format: string "ssh-rsa" + mpint exponent + mpint modulus
   */
  private byte[] encodeRsaPublicKey(RSAPublicKey key) throws IOException {
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    DataOutputStream dos = new DataOutputStream(baos);

    // Write key type
    byte[] keyType = "ssh-rsa".getBytes();
    dos.writeInt(keyType.length);
    dos.write(keyType);

    // Write exponent (as mpint - may need leading zero for positive numbers)
    byte[] exponent = key.getPublicExponent().toByteArray();
    dos.writeInt(exponent.length);
    dos.write(exponent);

    // Write modulus (as mpint - may need leading zero for positive numbers)
    byte[] modulus = key.getModulus().toByteArray();
    dos.writeInt(modulus.length);
    dos.write(modulus);

    return baos.toByteArray();
  }
}
