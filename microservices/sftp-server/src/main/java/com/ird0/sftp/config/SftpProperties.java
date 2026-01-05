package com.ird0.sftp.config;

import java.util.ArrayList;
import java.util.List;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "sftp")
public class SftpProperties {

  private ServerConfig server = new ServerConfig();
  private List<UserConfig> users = new ArrayList<>();

  @Data
  public static class ServerConfig {
    private int port = 2222;
    private String dataDirectory = "./data";
    private String hostKeyPath = "./keys/hostkey.pem";
    private int maxSessions = 10;
    private long sessionTimeout = 900000; // 15 minutes
  }

  @Data
  public static class UserConfig {
    private String username;
    private String publicKey;
  }
}
