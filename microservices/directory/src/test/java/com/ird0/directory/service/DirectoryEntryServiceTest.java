package com.ird0.directory.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ird0.commons.exception.EntityNotFoundException;
import com.ird0.directory.model.DirectoryEntry;
import com.ird0.directory.repository.DirectoryEntryRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DirectoryEntryServiceTest {

  @Mock private DirectoryEntryRepository repository;

  @InjectMocks private DirectoryEntryService service;

  private DirectoryEntry testEntry;
  private UUID testId;

  @BeforeEach
  void setUp() {
    testId = UUID.randomUUID();
    testEntry = new DirectoryEntry();
    testEntry.setId(testId);
    testEntry.setName("John Doe");
    testEntry.setType("individual");
    testEntry.setEmail("john@example.com");
    testEntry.setPhone("555-1234");
  }

  @Test
  void getAll_ReturnsAllEntries() {
    when(repository.findAll()).thenReturn(List.of(testEntry));

    List<DirectoryEntry> result = service.getAll();

    assertNotNull(result);
    assertEquals(1, result.size());
    assertEquals("John Doe", result.get(0).getName());
  }

  @Test
  void getById_ExistingId_ReturnsEntry() {
    when(repository.findById(testId)).thenReturn(Optional.of(testEntry));

    DirectoryEntry result = service.getById(testId);

    assertNotNull(result);
    assertEquals(testId, result.getId());
    assertEquals("John Doe", result.getName());
  }

  @Test
  void getById_NonExistingId_ThrowsException() {
    UUID unknownId = UUID.randomUUID();
    when(repository.findById(unknownId)).thenReturn(Optional.empty());

    assertThrows(EntityNotFoundException.class, () -> service.getById(unknownId));
  }

  @Test
  void create_ValidEntry_ReturnsSavedEntry() {
    DirectoryEntry newEntry = new DirectoryEntry();
    newEntry.setName("Jane Doe");
    newEntry.setType("individual");
    newEntry.setEmail("jane@example.com");
    newEntry.setPhone("555-5678");

    DirectoryEntry savedEntry = new DirectoryEntry();
    savedEntry.setId(UUID.randomUUID());
    savedEntry.setName("Jane Doe");
    savedEntry.setType("individual");
    savedEntry.setEmail("jane@example.com");
    savedEntry.setPhone("555-5678");

    when(repository.save(any(DirectoryEntry.class))).thenReturn(savedEntry);

    DirectoryEntry result = service.create(newEntry);

    assertNotNull(result);
    assertNotNull(result.getId());
    assertEquals("Jane Doe", result.getName());
    verify(repository, times(1)).save(any(DirectoryEntry.class));
  }

  @Test
  void update_ExistingEntry_ReturnsUpdatedEntry() {
    DirectoryEntry updatedEntry = new DirectoryEntry();
    updatedEntry.setId(testId);
    updatedEntry.setName("John Updated");
    updatedEntry.setType("corporate");
    updatedEntry.setEmail("john.updated@example.com");
    updatedEntry.setPhone("555-9999");

    when(repository.findById(testId)).thenReturn(Optional.of(testEntry));
    when(repository.save(any(DirectoryEntry.class))).thenReturn(updatedEntry);

    DirectoryEntry result = service.update(testId, updatedEntry);

    assertNotNull(result);
    assertEquals("John Updated", result.getName());
    assertEquals("corporate", result.getType());
    verify(repository, times(1)).save(any(DirectoryEntry.class));
  }

  @Test
  void update_NonExistingEntry_ThrowsException() {
    UUID unknownId = UUID.randomUUID();
    DirectoryEntry updateEntry = new DirectoryEntry();
    updateEntry.setName("Updated");

    when(repository.findById(unknownId)).thenReturn(Optional.empty());

    assertThrows(EntityNotFoundException.class, () -> service.update(unknownId, updateEntry));
  }

  @Test
  void delete_ExistingId_CallsRepositoryDelete() {
    service.delete(testId);

    verify(repository, times(1)).deleteById(testId);
  }
}
