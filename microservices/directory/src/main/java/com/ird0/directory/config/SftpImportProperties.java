package com.ird0.directory.config;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

/**
 * Configuration properties for SFTP import.
 *
 * <p>Authentication is handled via SSH certificates signed by Vault CA. See {@link
 * com.ird0.directory.config.ssh.SshCertificateManager} for certificate lifecycle management.
 */
@Data
@Component
@Validated
@ConfigurationProperties(prefix = "directory.sftp-import")
public class SftpImportProperties {

  private boolean enabled = false;

  @NotBlank(message = "SFTP host is required")
  private String host = "localhost";

  @Min(value = 1, message = "Port must be positive")
  private int port = 2222;

  /** Username must match certificate principal configured in Vault SSH role. */
  private String username = "policyholder-importer";

  @Min(value = 1000, message = "Connection timeout must be at least 1 second")
  private int connectionTimeout = 10000;

  private Polling polling = new Polling();
  private String localDirectory = "./temp/sftp-downloads";
  private String metadataDirectory = "./data/sftp-metadata";

  @Data
  public static class Polling {
    @Min(value = 1000, message = "Fixed delay must be at least 1 second")
    private long fixedDelay = 120000;

    @Min(value = 0, message = "Initial delay cannot be negative")
    private long initialDelay = 1000;

    @Min(value = 1, message = "Batch size must be at least 1")
    private int batchSize = 500;
  }

  @Data
  public static class ErrorHandling {
    private String errorDirectory = "./data/sftp-errors";
    private String deadLetterDirectory = "./data/sftp-failed";
    private boolean enabled = true;
  }

  @Data
  public static class Retry {
    @Min(value = 1, message = "Max attempts must be at least 1")
    private int maxAttempts = 3;

    @Min(value = 1000, message = "Initial delay must be at least 1 second")
    private long initialDelay = 5000;

    private double backoffMultiplier = 1.5;
    private long maxDelay = 300000;
    private boolean enabled = true;
  }

  private ErrorHandling errorHandling = new ErrorHandling();
  private Retry retry = new Retry();
}
