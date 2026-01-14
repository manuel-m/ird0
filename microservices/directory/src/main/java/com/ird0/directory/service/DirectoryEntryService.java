package com.ird0.directory.service;

import com.ird0.directory.exception.EntityNotFoundException;
import com.ird0.directory.model.DirectoryEntry;
import com.ird0.directory.repository.DirectoryEntryRepository;
import java.util.List;
import java.util.UUID;
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

  public DirectoryEntry getById(UUID id) {
    return repository
        .findById(id)
        .orElseThrow(() -> new EntityNotFoundException("Entry not found with id: " + id));
  }

  public DirectoryEntry create(DirectoryEntry entry) {
    return repository.save(entry);
  }

  public DirectoryEntry update(UUID id, DirectoryEntry entry) {
    DirectoryEntry existing = getById(id);
    existing.setName(entry.getName());
    existing.setType(entry.getType());
    existing.setEmail(entry.getEmail());
    existing.setPhone(entry.getPhone());
    existing.setAddress(entry.getAddress());
    existing.setAdditionalInfo(entry.getAdditionalInfo());
    return repository.save(existing);
  }

  public void delete(UUID id) {
    repository.deleteById(id);
  }
}
