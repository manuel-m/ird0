package com.ird0.sftp.lifecycle;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.sshd.server.SshServer;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class SftpServerLifecycle {

  private final SshServer sshServer;

  @PostConstruct
  public void start() throws Exception {
    log.info("Starting SFTP Server on port {}", sshServer.getPort());
    sshServer.start();
    log.info("SFTP Server started successfully");
  }

  @PreDestroy
  public void stop() throws Exception {
    if (sshServer != null && sshServer.isStarted()) {
      log.info("Stopping SFTP Server...");
      sshServer.stop(true);
      log.info("SFTP Server stopped successfully");
    }
  }
}
