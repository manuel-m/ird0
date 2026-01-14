package com.ird0.sftp.config;

import com.ird0.sftp.auth.CertificateAuthenticator;
import com.ird0.sftp.filesystem.ReadOnlyFileSystemFactory;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.sshd.server.SshServer;
import org.apache.sshd.server.keyprovider.SimpleGeneratorHostKeyProvider;
import org.apache.sshd.sftp.server.SftpSubsystemFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Slf4j
@Configuration
@RequiredArgsConstructor
public class SftpServerConfig {

  private final SftpProperties properties;

  @Bean
  public SshServer sshServer(
      CertificateAuthenticator certificateAuthenticator,
      ReadOnlyFileSystemFactory fileSystemFactory,
      Optional<VaultHostKeyProvider> vaultHostKeyProvider)
      throws IOException {

    SshServer sshd = SshServer.setUpDefaultServer();

    // Set SFTP port
    sshd.setPort(properties.getServer().getPort());

    // Configure host key - use Vault if available, otherwise file-based
    configureHostKey(sshd, vaultHostKeyProvider);

    // Set certificate authenticator (validates SSH certificates signed by Vault CA)
    sshd.setPublickeyAuthenticator(certificateAuthenticator);
    log.info("Certificate-based authentication enabled (Vault SSH CA mode)");

    // Disable password authentication
    sshd.setPasswordAuthenticator(null);

    // Configure SFTP subsystem
    sshd.setSubsystemFactories(List.of(new SftpSubsystemFactory()));

    // Set file system factory (read-only)
    sshd.setFileSystemFactory(fileSystemFactory);

    // Configure session properties
    sshd.getProperties()
        .put("max-concurrent-sessions", String.valueOf(properties.getServer().getMaxSessions()));

    sshd.getProperties()
        .put("idle-timeout", String.valueOf(properties.getServer().getSessionTimeout()));

    log.info("SFTP Server configured on port {}", properties.getServer().getPort());

    return sshd;
  }

  private void configureHostKey(SshServer sshd, Optional<VaultHostKeyProvider> vaultHostKeyProvider)
      throws IOException {
    if (vaultHostKeyProvider.isPresent()) {
      // Use Vault-provided host key
      sshd.setKeyPairProvider(vaultHostKeyProvider.get());
      log.info("Host key configured from Vault");
    } else {
      // Fall back to file-based host key
      configureFileBasedHostKey(sshd);
    }
  }

  private void configureFileBasedHostKey(SshServer sshd) throws IOException {
    Path hostKeyPath = Paths.get(properties.getServer().getHostKeyPath());

    // Create parent directory if it doesn't exist
    Files.createDirectories(hostKeyPath.getParent());

    // Use SimpleGeneratorHostKeyProvider to auto-generate if not exists
    SimpleGeneratorHostKeyProvider hostKeyProvider =
        new SimpleGeneratorHostKeyProvider(hostKeyPath);
    hostKeyProvider.setAlgorithm("RSA");

    sshd.setKeyPairProvider(hostKeyProvider);

    log.info("Host key configured at: {}", hostKeyPath);
  }
}
