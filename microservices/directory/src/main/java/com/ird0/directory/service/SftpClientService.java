package com.ird0.directory.service;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.integration.sftp.session.SftpRemoteFileTemplate;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "directory.sftp-import", name = "enabled", havingValue = "true")
public class SftpClientService {

  private final SftpRemoteFileTemplate sftpTemplate;

  public InputStream downloadFile(String remoteFilePath) throws IOException {
    log.info("Downloading file from SFTP: {}", remoteFilePath);

    try {
      ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

      sftpTemplate.get(
          remoteFilePath,
          inputStream -> {
            byte[] buffer = new byte[8192];
            int bytesRead;
            while ((bytesRead = inputStream.read(buffer)) != -1) {
              outputStream.write(buffer, 0, bytesRead);
            }
          });

      byte[] fileData = outputStream.toByteArray();
      log.info(
          "Successfully downloaded file from SFTP: {} ({} bytes)", remoteFilePath, fileData.length);

      return new ByteArrayInputStream(fileData);

    } catch (Exception e) {
      log.error("Failed to download file from SFTP: {}", remoteFilePath, e);
      throw new IOException("Failed to download file from SFTP: " + remoteFilePath, e);
    }
  }
}
