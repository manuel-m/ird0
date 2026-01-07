package com.ird0.directory.repository;

import com.ird0.directory.model.DirectoryEntry;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DirectoryEntryRepository extends JpaRepository<DirectoryEntry, Long> {

  Optional<DirectoryEntry> findByEmail(String email);

  List<DirectoryEntry> findByEmailIn(Collection<String> emails);

  @Modifying
  @Query(
      value =
          """
        INSERT INTO directory_entry (name, type, email, phone, address, additional_info)
        VALUES (:#{#entry.name}, :#{#entry.type}, :#{#entry.email},
                :#{#entry.phone}, :#{#entry.address}, :#{#entry.additionalInfo})
        ON CONFLICT (email) DO UPDATE SET
            name = EXCLUDED.name,
            type = EXCLUDED.type,
            phone = EXCLUDED.phone,
            address = EXCLUDED.address,
            additional_info = EXCLUDED.additional_info
        """,
      nativeQuery = true)
  void upsertByEmail(@Param("entry") DirectoryEntry entry);
}
