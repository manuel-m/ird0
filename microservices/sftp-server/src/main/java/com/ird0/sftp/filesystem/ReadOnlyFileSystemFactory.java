package com.ird0.sftp.filesystem;

import com.ird0.sftp.config.SftpProperties;
import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.Paths;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.sshd.common.file.FileSystemFactory;
import org.apache.sshd.common.session.SessionContext;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class ReadOnlyFileSystemFactory implements FileSystemFactory {

  private final SftpProperties properties;

  @Override
  public FileSystem createFileSystem(SessionContext session) throws IOException {
    String username = session.getUsername();
    Path dataPath = Paths.get(properties.getServer().getDataDirectory());

    log.info("Creating read-only file system for user {} at path {}", username, dataPath);

    // Return a custom read-only file system view
    return new CsvVirtualFileSystemView(dataPath, FileSystems.getDefault());
  }

  @Override
  public Path getUserHomeDir(SessionContext session) throws IOException {
    // Return the data directory as the home directory for all users
    return Paths.get(properties.getServer().getDataDirectory());
  }
}
