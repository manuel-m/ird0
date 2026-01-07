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

  private Polling polling = new Polling();
  private String localDirectory = "./temp/sftp-downloads";
  private String metadataDirectory = "./data/sftp-metadata";

  @Data
  public static class Polling {
    private long fixedDelay = 120000;
    private long initialDelay = 1000;
    private int batchSize = 500;
  }
}
