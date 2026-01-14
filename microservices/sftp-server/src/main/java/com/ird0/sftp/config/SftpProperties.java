package com.ird0.sftp.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Configuration properties for SFTP server.
 *
 * <p>Authentication is handled via SSH certificates signed by Vault CA. See {@link
 * com.ird0.sftp.auth.CertificateAuthenticator} for details.
 */
@Data
@Component
@ConfigurationProperties(prefix = "sftp")
public class SftpProperties {

  private ServerConfig server = new ServerConfig();

  @Data
  public static class ServerConfig {
    private int port = 2222;
    private String dataDirectory = "./data";
    private String hostKeyPath = "./keys/hostkey.pem";
    private int maxSessions = 10;
    private long sessionTimeout = 900000; // 15 minutes
  }
}
