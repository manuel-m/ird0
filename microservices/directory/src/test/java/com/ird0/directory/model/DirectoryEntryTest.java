package com.ird0.directory.model;

import static org.junit.jupiter.api.Assertions.*;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class DirectoryEntryTest {

  @Test
  void testGenerateId_WhenIdIsNull_ShouldGenerateUUID() {
    DirectoryEntry entry = new DirectoryEntry();
    assertNull(entry.getId(), "ID should initially be null");

    entry.generateId();

    assertNotNull(entry.getId(), "ID should be generated");
    assertTrue(entry.getId() instanceof UUID, "ID should be a UUID");
  }

  @Test
  void testGenerateId_WhenIdAlreadySet_ShouldNotOverwrite() {
    DirectoryEntry entry = new DirectoryEntry();
    UUID existingId = UUID.randomUUID();
    entry.setId(existingId);

    entry.generateId();

    assertEquals(existingId, entry.getId(), "Existing ID should not be overwritten");
  }

  @Test
  void testUUIDIsUnique() {
    DirectoryEntry entry1 = new DirectoryEntry();
    DirectoryEntry entry2 = new DirectoryEntry();

    entry1.generateId();
    entry2.generateId();

    assertNotEquals(entry1.getId(), entry2.getId(), "Generated UUIDs should be unique");
  }
}
