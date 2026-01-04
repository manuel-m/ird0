package com.ird0.directory.service;

import com.ird0.directory.model.DirectoryEntry;
import com.ird0.directory.repository.DirectoryEntryRepository;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class DirectoryEntryService {

  private final DirectoryEntryRepository repository;

  public DirectoryEntryService(DirectoryEntryRepository repository) {
    this.repository = repository;
  }

  public List<DirectoryEntry> getAll() {
    return repository.findAll();
  }

  public DirectoryEntry getById(Long id) {
    return repository
        .findById(id)
        .orElseThrow(() -> new RuntimeException("Entry not found with id: " + id));
  }

  public DirectoryEntry create(DirectoryEntry entry) {
    return repository.save(entry);
  }

  public DirectoryEntry update(Long id, DirectoryEntry entry) {
    DirectoryEntry existing = getById(id);
    existing.setName(entry.getName());
    existing.setType(entry.getType());
    existing.setEmail(entry.getEmail());
    existing.setPhone(entry.getPhone());
    existing.setAddress(entry.getAddress());
    existing.setAdditionalInfo(entry.getAdditionalInfo());
    return repository.save(existing);
  }

  public void delete(Long id) {
    repository.deleteById(id);
  }
}
