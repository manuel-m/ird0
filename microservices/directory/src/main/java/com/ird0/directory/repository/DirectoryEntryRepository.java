package com.ird0.directory.repository;

import com.ird0.directory.model.DirectoryEntry;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DirectoryEntryRepository extends JpaRepository<DirectoryEntry, Long> {
}
