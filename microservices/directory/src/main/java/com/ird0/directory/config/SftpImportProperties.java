package com.ird0.directory.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "directory.sftp-import")
public class SftpImportProperties {

  private boolean enabled = false;
  private String host = "localhost";
  private int port = 2222;
  private String username = "policyholder-importer";
  private String privateKeyPath = "./keys/sftp_client_key";
  private String remoteFilePath = "/policyholders.csv";
  private int connectionTimeout = 10000;
}
