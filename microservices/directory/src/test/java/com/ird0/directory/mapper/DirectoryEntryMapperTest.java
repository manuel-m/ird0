package com.ird0.directory.mapper;

import static org.junit.jupiter.api.Assertions.*;

import com.ird0.directory.dto.DirectoryEntryDTO;
import com.ird0.directory.model.DirectoryEntry;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

class DirectoryEntryMapperTest {

  private DirectoryEntryMapper mapper;

  @BeforeEach
  void setUp() {
    mapper = Mappers.getMapper(DirectoryEntryMapper.class);
  }

  @Test
  void testToDTO_ConvertsEntityToDTO() {
    DirectoryEntry entity = new DirectoryEntry();
    entity.setId(UUID.randomUUID());
    entity.setName("John Doe");
    entity.setType("individual");
    entity.setEmail("john@example.com");
    entity.setPhone("555-1234");
    entity.setAddress("123 Main St");
    entity.setAdditionalInfo("Test");

    DirectoryEntryDTO dto = mapper.toDTO(entity);

    assertNotNull(dto);
    assertEquals(entity.getId(), dto.id());
    assertEquals(entity.getName(), dto.name());
    assertEquals(entity.getType(), dto.type());
    assertEquals(entity.getEmail(), dto.email());
    assertEquals(entity.getPhone(), dto.phone());
    assertEquals(entity.getAddress(), dto.address());
    assertEquals(entity.getAdditionalInfo(), dto.additionalInfo());
  }

  @Test
  void testToEntity_ConvertsDTOToEntity() {
    DirectoryEntryDTO dto =
        new DirectoryEntryDTO(
            null,
            "Jane Smith",
            "family",
            "jane@example.com",
            "555-5678",
            "456 Oak Ave",
            "Family account",
            null);

    DirectoryEntry entity = mapper.toEntity(dto);

    assertNotNull(entity);
    assertEquals(dto.name(), entity.getName());
    assertEquals(dto.type(), entity.getType());
    assertEquals(dto.email(), entity.getEmail());
    assertEquals(dto.phone(), entity.getPhone());
    assertEquals(dto.address(), entity.getAddress());
    assertEquals(dto.additionalInfo(), entity.getAdditionalInfo());
  }

  @Test
  void testToEntity_WithNullId_ShouldCreateEntityWithNullId() {
    DirectoryEntryDTO dto =
        new DirectoryEntryDTO(
            null, "Test", "individual", "test@example.com", "555-0000", null, null, null);

    DirectoryEntry entity = mapper.toEntity(dto);

    assertNotNull(entity);
    assertNull(entity.getId(), "Entity ID should be null when DTO ID is null");
  }

  @Test
  void testUpdateEntityFromDTO_UpdatesExistingEntity() {
    DirectoryEntry existingEntity = new DirectoryEntry();
    existingEntity.setId(UUID.randomUUID());
    existingEntity.setName("Original Name");
    existingEntity.setType("individual");
    existingEntity.setEmail("original@example.com");
    existingEntity.setPhone("555-0000");

    DirectoryEntryDTO updateDTO =
        new DirectoryEntryDTO(
            null,
            "Updated Name",
            "family",
            "updated@example.com",
            "555-9999",
            "New Address",
            null,
            null);

    mapper.updateEntityFromDTO(updateDTO, existingEntity);

    assertEquals("Updated Name", existingEntity.getName());
    assertEquals("family", existingEntity.getType());
    assertEquals("updated@example.com", existingEntity.getEmail());
    assertEquals("555-9999", existingEntity.getPhone());
    assertEquals("New Address", existingEntity.getAddress());
    assertNotNull(existingEntity.getId(), "ID should remain unchanged");
  }

  @Test
  void testToDTOList_ConvertsListOfEntities() {
    DirectoryEntry entity1 = new DirectoryEntry();
    entity1.setId(UUID.randomUUID());
    entity1.setName("Entity 1");
    entity1.setType("individual");
    entity1.setEmail("entity1@example.com");
    entity1.setPhone("555-0001");

    DirectoryEntry entity2 = new DirectoryEntry();
    entity2.setId(UUID.randomUUID());
    entity2.setName("Entity 2");
    entity2.setType("family");
    entity2.setEmail("entity2@example.com");
    entity2.setPhone("555-0002");

    List<DirectoryEntry> entities = Arrays.asList(entity1, entity2);

    List<DirectoryEntryDTO> dtos = mapper.toDTOList(entities);

    assertNotNull(dtos);
    assertEquals(2, dtos.size());
    assertEquals(entity1.getName(), dtos.get(0).name());
    assertEquals(entity2.getName(), dtos.get(1).name());
  }

  @Test
  void testToDTO_WithNullValues_ShouldHandleGracefully() {
    DirectoryEntry entity = new DirectoryEntry();
    entity.setId(UUID.randomUUID());
    entity.setName("Test");
    entity.setType("individual");
    entity.setEmail("test@example.com");
    entity.setPhone("555-0000");
    entity.setAddress(null);
    entity.setAdditionalInfo(null);

    DirectoryEntryDTO dto = mapper.toDTO(entity);

    assertNotNull(dto);
    assertNull(dto.address());
    assertNull(dto.additionalInfo());
  }
}
