package com.ird0.sftp.filesystem;

import java.io.IOException;
import java.nio.file.FileStore;
import java.nio.file.FileSystem;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.nio.file.Paths;
import java.nio.file.WatchService;
import java.nio.file.attribute.UserPrincipalLookupService;
import java.nio.file.spi.FileSystemProvider;
import java.util.Set;
import lombok.RequiredArgsConstructor;

/**
 * A read-only file system view that wraps the default file system and restricts operations to
 * read-only access within a specific directory.
 */
@RequiredArgsConstructor
public class CsvVirtualFileSystemView extends FileSystem {

  private final Path rootPath;
  private final FileSystem delegate;

  @Override
  public FileSystemProvider provider() {
    return delegate.provider();
  }

  @Override
  public void close() throws IOException {
    // Don't close the delegate - it's the default filesystem
  }

  @Override
  public boolean isOpen() {
    return delegate.isOpen();
  }

  @Override
  public boolean isReadOnly() {
    return true; // Always read-only
  }

  @Override
  public String getSeparator() {
    return delegate.getSeparator();
  }

  @Override
  public Iterable<Path> getRootDirectories() {
    return Set.of(rootPath);
  }

  @Override
  public Iterable<FileStore> getFileStores() {
    return delegate.getFileStores();
  }

  @Override
  public Set<String> supportedFileAttributeViews() {
    return delegate.supportedFileAttributeViews();
  }

  @Override
  public Path getPath(String first, String... more) {
    // Resolve paths relative to root
    Path requested = Paths.get(first, more);
    Path resolved = rootPath.resolve(requested).normalize();

    // Security: Ensure resolved path stays within rootPath
    if (!resolved.startsWith(rootPath)) {
      throw new SecurityException("Path traversal attempt blocked: " + requested);
    }
    return resolved;
  }

  @Override
  public PathMatcher getPathMatcher(String syntaxAndPattern) {
    return delegate.getPathMatcher(syntaxAndPattern);
  }

  @Override
  public UserPrincipalLookupService getUserPrincipalLookupService() {
    return delegate.getUserPrincipalLookupService();
  }

  @Override
  public WatchService newWatchService() throws IOException {
    throw new UnsupportedOperationException("Watch service not supported");
  }
}
