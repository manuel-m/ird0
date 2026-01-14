package com.ird0.directory.config.ssh;

import java.time.Duration;
import lombok.Data;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Configuration properties for SSH certificate management.
 *
 * <p>These properties control how SSH certificates are requested from Vault and when they should be
 * renewed.
 */
@Data
@Component
@ConfigurationProperties(prefix = "directory.sftp-import.certificate")
@ConditionalOnProperty(name = "directory.sftp-import.enabled", havingValue = "true")
public class SshCertificateProperties {

  /** Certificate TTL (default: 15 minutes for high security). */
  private Duration ttl = Duration.ofMinutes(15);

  /** Renewal threshold - renew when this much time remains (default: 5 minutes). */
  private Duration renewalThreshold = Duration.ofMinutes(5);

  /** Principal/username for the certificate. */
  private String principal = "policyholder-importer";

  /** Vault SSH role name for certificate signing. */
  private String vaultRole = "directory-service";
}
