package com.ird0.directory.config.ssh;

import com.ird0.directory.config.SftpImportProperties;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.KeyPair;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.sshd.client.SshClient;
import org.apache.sshd.client.session.ClientSession;
import org.apache.sshd.sftp.client.SftpClient;
import org.apache.sshd.sftp.client.SftpClient.DirEntry;
import org.apache.sshd.sftp.client.SftpClientFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * SFTP client using Apache MINA SSHD with certificate-based authentication.
 *
 * <p>This client replaces Spring Integration SFTP to provide native support for SSH certificates
 * signed by Vault CA. Key features:
 *
 * <ul>
 *   <li>Uses ephemeral SSH certificates from {@link SshCertificateManager}
 *   <li>No private key material written to disk
 *   <li>Automatic certificate renewal via manager
 *   <li>Native OpenSSH certificate support in Apache MINA SSHD
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "directory.sftp-import.enabled", havingValue = "true")
public class MinaSftpClient {

  private final SshCertificateManager certificateManager;
  private final SftpImportProperties properties;

  private SshClient sshClient;

  @PostConstruct
  public void init() {
    sshClient = SshClient.setUpDefaultClient();
    sshClient.start();
    log.info(
        "Apache MINA SSHD client started for {}:{}", properties.getHost(), properties.getPort());
  }

  /**
   * Downloads a file from the SFTP server.
   *
   * @param remotePath the remote file path
   * @param localPath the local destination path
   * @throws IOException if download fails
   */
  public void downloadFile(String remotePath, Path localPath) throws IOException {
    try (ClientSession session = createSession();
        SftpClient sftp = createSftpClient(session)) {

      log.info("Downloading {} to {}", remotePath, localPath);

      // Ensure parent directory exists
      Files.createDirectories(localPath.getParent());

      try (InputStream is = sftp.read(remotePath)) {
        Files.copy(is, localPath, StandardCopyOption.REPLACE_EXISTING);
      }

      log.info("Downloaded {} successfully", remotePath);
    }
  }

  /**
   * Lists files in a remote directory.
   *
   * @param remotePath the remote directory path
   * @return iterable of directory entries
   * @throws IOException if listing fails
   */
  public Iterable<DirEntry> listFiles(String remotePath) throws IOException {
    try (ClientSession session = createSession();
        SftpClient sftp = createSftpClient(session)) {

      log.debug("Listing files in {}", remotePath);
      // Collect to list since session closes after this method
      java.util.List<DirEntry> entries = new java.util.ArrayList<>();
      for (DirEntry entry : sftp.readDir(remotePath)) {
        entries.add(entry);
      }
      return entries;
    }
  }

  /**
   * Checks if a remote file exists.
   *
   * @param remotePath the remote file path
   * @return true if file exists
   * @throws IOException if check fails
   */
  public boolean fileExists(String remotePath) throws IOException {
    try (ClientSession session = createSession();
        SftpClient sftp = createSftpClient(session)) {

      try {
        sftp.stat(remotePath);
        return true;
      } catch (IOException e) {
        return false;
      }
    }
  }

  /**
   * Gets the last modification time of a remote file.
   *
   * @param remotePath the remote file path
   * @return modification time in milliseconds since epoch
   * @throws IOException if stat fails
   */
  public long getLastModified(String remotePath) throws IOException {
    try (ClientSession session = createSession();
        SftpClient sftp = createSftpClient(session)) {

      SftpClient.Attributes attrs = sftp.stat(remotePath);
      // SFTP returns modification time in seconds, convert to milliseconds
      return attrs.getModifyTime().toMillis();
    }
  }

  private ClientSession createSession() throws IOException {
    SignedCertificate cert = certificateManager.getCurrentCertificate();
    KeyPair keyPair = cert.getKeyPair();

    log.debug(
        "Creating SSH session with certificate serial={}, expiresAt={}",
        cert.getSerial(),
        cert.getExpiresAt());

    long timeoutMs = properties.getConnectionTimeout();

    ClientSession session =
        sshClient
            .connect(properties.getUsername(), properties.getHost(), properties.getPort())
            .verify(Duration.ofMillis(timeoutMs))
            .getSession();

    // Add the key pair for authentication
    // Apache MINA SSHD will use the certificate if available
    session.addPublicKeyIdentity(keyPair);

    // Authenticate
    session.auth().verify(Duration.ofMillis(timeoutMs));

    log.debug("SSH session authenticated successfully");
    return session;
  }

  private SftpClient createSftpClient(ClientSession session) throws IOException {
    SftpClientFactory factory = SftpClientFactory.instance();
    return factory.createSftpClient(session);
  }

  @PreDestroy
  public void shutdown() {
    if (sshClient != null && sshClient.isStarted()) {
      log.info("Stopping Apache MINA SSHD client");
      sshClient.stop();
    }
  }
}
