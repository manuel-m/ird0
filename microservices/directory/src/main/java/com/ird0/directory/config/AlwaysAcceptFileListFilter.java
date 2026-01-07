package com.ird0.directory.config;

import java.util.Arrays;
import java.util.List;
import org.apache.sshd.sftp.client.SftpClient;
import org.springframework.integration.file.filters.FileListFilter;

public class AlwaysAcceptFileListFilter implements FileListFilter<SftpClient.DirEntry> {

  @Override
  public List<SftpClient.DirEntry> filterFiles(SftpClient.DirEntry[] files) {
    return Arrays.asList(files);
  }

  @Override
  public boolean supportsSingleFileFiltering() {
    return false;
  }
}
