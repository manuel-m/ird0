package com.ird0.directory.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ird0.directory.model.DirectoryEntry;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@DataJpaTest
@Testcontainers(disabledWithoutDocker = true)
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@EnabledIf("isDockerAvailable")
class DirectoryEntryRepositoryTest {

  @Container
  static PostgreSQLContainer<?> postgres =
      new PostgreSQLContainer<>("postgres:16-alpine").withDatabaseName("testdb");

  static boolean isDockerAvailable() {
    try {
      return DockerClientFactory.instance().isDockerAvailable();
    } catch (Exception e) {
      return false;
    }
  }

  @DynamicPropertySource
  static void configureProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", postgres::getJdbcUrl);
    registry.add("spring.datasource.username", postgres::getUsername);
    registry.add("spring.datasource.password", postgres::getPassword);
  }

  @Autowired private DirectoryEntryRepository repository;

  private DirectoryEntry testEntry;

  @BeforeEach
  void setUp() {
    repository.deleteAll();

    testEntry = new DirectoryEntry();
    testEntry.setName("John Doe");
    testEntry.setType("individual");
    testEntry.setEmail("john@example.com");
    testEntry.setPhone("555-1234");
    testEntry.setAddress("123 Main St");
  }

  @Test
  void save_NewEntry_AssignsUUID() {
    DirectoryEntry saved = repository.save(testEntry);

    assertNotNull(saved.getId());
    assertEquals("John Doe", saved.getName());
  }

  @Test
  void findById_ExistingEntry_ReturnsEntry() {
    DirectoryEntry saved = repository.save(testEntry);

    Optional<DirectoryEntry> found = repository.findById(saved.getId());

    assertTrue(found.isPresent());
    assertEquals("John Doe", found.get().getName());
  }

  @Test
  void findById_NonExistingEntry_ReturnsEmpty() {
    Optional<DirectoryEntry> found = repository.findById(UUID.randomUUID());

    assertTrue(found.isEmpty());
  }

  @Test
  void findAll_MultipleEntries_ReturnsAll() {
    DirectoryEntry entry1 = new DirectoryEntry();
    entry1.setName("John Doe");
    entry1.setType("individual");
    entry1.setEmail("john@example.com");
    entry1.setPhone("555-1234");

    DirectoryEntry entry2 = new DirectoryEntry();
    entry2.setName("Jane Doe");
    entry2.setType("corporate");
    entry2.setEmail("jane@example.com");
    entry2.setPhone("555-5678");

    repository.save(entry1);
    repository.save(entry2);

    List<DirectoryEntry> all = repository.findAll();

    assertEquals(2, all.size());
  }

  @Test
  void findByEmail_ExistingEmail_ReturnsEntry() {
    repository.save(testEntry);

    Optional<DirectoryEntry> found = repository.findByEmail("john@example.com");

    assertTrue(found.isPresent());
    assertEquals("John Doe", found.get().getName());
  }

  @Test
  void findByEmail_NonExistingEmail_ReturnsEmpty() {
    Optional<DirectoryEntry> found = repository.findByEmail("unknown@example.com");

    assertTrue(found.isEmpty());
  }

  @Test
  void delete_ExistingEntry_RemovesEntry() {
    DirectoryEntry saved = repository.save(testEntry);
    UUID savedId = saved.getId();

    repository.deleteById(savedId);

    Optional<DirectoryEntry> found = repository.findById(savedId);
    assertTrue(found.isEmpty());
  }

  @Test
  void update_ExistingEntry_UpdatesFields() {
    DirectoryEntry saved = repository.save(testEntry);
    saved.setName("John Updated");
    saved.setPhone("555-9999");

    DirectoryEntry updated = repository.save(saved);

    assertEquals("John Updated", updated.getName());
    assertEquals("555-9999", updated.getPhone());
    assertEquals(saved.getId(), updated.getId());
  }
}
