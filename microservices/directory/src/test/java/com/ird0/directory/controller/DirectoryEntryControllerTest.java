package com.ird0.directory.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ird0.commons.exception.EntityNotFoundException;
import com.ird0.commons.exception.GlobalExceptionHandler;
import com.ird0.directory.dto.DirectoryEntryDTO;
import com.ird0.directory.mapper.DirectoryEntryMapper;
import com.ird0.directory.model.DirectoryEntry;
import com.ird0.directory.service.CsvImportService;
import com.ird0.directory.service.DirectoryEntryService;
import com.ird0.directory.service.ImportAuditService;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(DirectoryEntryController.class)
@Import(GlobalExceptionHandler.class)
@ActiveProfiles("test")
class DirectoryEntryControllerTest {

  @Autowired private MockMvc mockMvc;

  @Autowired private ObjectMapper objectMapper;

  @MockitoBean private DirectoryEntryService service;

  @MockitoBean private DirectoryEntryMapper mapper;

  @MockitoBean private CsvImportService csvImportService;

  @MockitoBean private ImportAuditService auditService;

  private DirectoryEntry testEntity;
  private DirectoryEntryDTO testDto;
  private UUID testId;

  @BeforeEach
  void setUp() {
    testId = UUID.randomUUID();

    testEntity = new DirectoryEntry();
    testEntity.setId(testId);
    testEntity.setName("John Doe");
    testEntity.setType("individual");
    testEntity.setEmail("john@example.com");
    testEntity.setPhone("555-1234");
    testEntity.setAddress("123 Main St");

    testDto =
        new DirectoryEntryDTO(
            testId,
            "John Doe",
            "individual",
            "john@example.com",
            "555-1234",
            "123 Main St",
            null,
            null);
  }

  @Test
  void getAll_ReturnsListOfEntries() throws Exception {
    when(service.getAll()).thenReturn(List.of(testEntity));
    when(mapper.toDTOList(any())).thenReturn(List.of(testDto));

    mockMvc
        .perform(get("/api/entries"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].name").value("John Doe"))
        .andExpect(jsonPath("$[0].email").value("john@example.com"));
  }

  @Test
  void getOne_ReturnsEntry() throws Exception {
    when(service.getById(testId)).thenReturn(testEntity);
    when(mapper.toDTO(testEntity)).thenReturn(testDto);

    mockMvc
        .perform(get("/api/entries/{id}", testId))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.name").value("John Doe"))
        .andExpect(jsonPath("$.email").value("john@example.com"));
  }

  @Test
  void getOne_NotFound_Returns404() throws Exception {
    UUID unknownId = UUID.randomUUID();
    when(service.getById(unknownId))
        .thenThrow(new EntityNotFoundException("Entry not found with id: " + unknownId));

    mockMvc.perform(get("/api/entries/{id}", unknownId)).andExpect(status().isNotFound());
  }

  @Test
  void create_ValidDto_ReturnsCreatedEntry() throws Exception {
    DirectoryEntryDTO createDto =
        new DirectoryEntryDTO(
            null, "Jane Doe", "individual", "jane@example.com", "555-5678", null, null, null);

    DirectoryEntry newEntity = new DirectoryEntry();
    UUID newId = UUID.randomUUID();
    newEntity.setId(newId);
    newEntity.setName("Jane Doe");
    newEntity.setType("individual");
    newEntity.setEmail("jane@example.com");
    newEntity.setPhone("555-5678");

    DirectoryEntryDTO responseDto =
        new DirectoryEntryDTO(
            newId, "Jane Doe", "individual", "jane@example.com", "555-5678", null, null, null);

    when(mapper.toEntity(any(DirectoryEntryDTO.class))).thenReturn(newEntity);
    when(service.create(any(DirectoryEntry.class))).thenReturn(newEntity);
    when(mapper.toDTO(newEntity)).thenReturn(responseDto);

    mockMvc
        .perform(
            post("/api/entries")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(createDto)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.name").value("Jane Doe"))
        .andExpect(jsonPath("$.email").value("jane@example.com"));
  }

  @Test
  void create_InvalidDto_Returns400() throws Exception {
    DirectoryEntryDTO invalidDto =
        new DirectoryEntryDTO(null, "", null, "invalid-email", null, null, null, null);

    mockMvc
        .perform(
            post("/api/entries")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(invalidDto)))
        .andExpect(status().isBadRequest());
  }

  @Test
  void update_ValidDto_ReturnsUpdatedEntry() throws Exception {
    DirectoryEntryDTO updateDto =
        new DirectoryEntryDTO(
            null,
            "John Updated",
            "individual",
            "john.updated@example.com",
            "555-9999",
            null,
            null,
            null);

    DirectoryEntry updatedEntity = new DirectoryEntry();
    updatedEntity.setId(testId);
    updatedEntity.setName("John Updated");
    updatedEntity.setType("individual");
    updatedEntity.setEmail("john.updated@example.com");
    updatedEntity.setPhone("555-9999");

    DirectoryEntryDTO responseDto =
        new DirectoryEntryDTO(
            testId,
            "John Updated",
            "individual",
            "john.updated@example.com",
            "555-9999",
            null,
            null,
            null);

    when(service.getById(testId)).thenReturn(testEntity);
    when(service.update(any(UUID.class), any(DirectoryEntry.class))).thenReturn(updatedEntity);
    when(mapper.toDTO(updatedEntity)).thenReturn(responseDto);

    mockMvc
        .perform(
            put("/api/entries/{id}", testId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(updateDto)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.name").value("John Updated"));
  }

  @Test
  void delete_ExistingEntry_Returns200() throws Exception {
    mockMvc.perform(delete("/api/entries/{id}", testId)).andExpect(status().isOk());
  }
}
