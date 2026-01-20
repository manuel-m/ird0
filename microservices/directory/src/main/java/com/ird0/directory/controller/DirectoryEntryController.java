package com.ird0.directory.controller;

import com.ird0.directory.dto.AuditRecord;
import com.ird0.directory.dto.DirectoryEntryDTO;
import com.ird0.directory.dto.ImportResult;
import com.ird0.directory.mapper.DirectoryEntryMapper;
import com.ird0.directory.model.DirectoryEntry;
import com.ird0.directory.service.CsvImportService;
import com.ird0.directory.service.DirectoryEntryService;
import com.ird0.directory.service.ImportAuditService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@Slf4j
@RestController
@RequestMapping("${directory.api.base-path:/api/entries}")
@RequiredArgsConstructor
@Tag(name = "Directory Entries", description = "CRUD operations for directory entries")
public class DirectoryEntryController {

  private final DirectoryEntryService service;
  private final DirectoryEntryMapper mapper;
  private final CsvImportService csvImportService;
  private final ImportAuditService auditService;

  @Operation(summary = "List all directory entries", operationId = "getAllEntries")
  @ApiResponse(responseCode = "200", description = "List of entries")
  @GetMapping
  public List<DirectoryEntryDTO> getAll() {
    List<DirectoryEntry> entities = service.getAll();
    return mapper.toDTOList(entities);
  }

  @Operation(summary = "Get entry by ID", operationId = "getEntryById")
  @ApiResponse(responseCode = "200", description = "Entry found")
  @ApiResponse(responseCode = "404", description = "Entry not found")
  @GetMapping("/{id}")
  public DirectoryEntryDTO getOne(@PathVariable UUID id) {
    DirectoryEntry entity = service.getById(id);
    return mapper.toDTO(entity);
  }

  @Operation(summary = "Create new entry", operationId = "createEntry")
  @ApiResponse(responseCode = "200", description = "Entry created")
  @PostMapping
  public DirectoryEntryDTO create(@Valid @RequestBody DirectoryEntryDTO dto) {
    DirectoryEntry entity = mapper.toEntity(dto);
    DirectoryEntry saved = service.create(entity);
    return mapper.toDTO(saved);
  }

  @Operation(summary = "Update existing entry", operationId = "updateEntry")
  @ApiResponse(responseCode = "200", description = "Entry updated")
  @ApiResponse(responseCode = "404", description = "Entry not found")
  @PutMapping("/{id}")
  public DirectoryEntryDTO update(
      @PathVariable UUID id, @Valid @RequestBody DirectoryEntryDTO dto) {
    DirectoryEntry existing = service.getById(id);
    mapper.updateEntityFromDTO(dto, existing);
    DirectoryEntry updated = service.update(id, existing);
    return mapper.toDTO(updated);
  }

  @Operation(summary = "Delete entry by ID", operationId = "deleteEntry")
  @ApiResponse(responseCode = "200", description = "Entry deleted")
  @ApiResponse(responseCode = "404", description = "Entry not found")
  @DeleteMapping("/{id}")
  public void delete(@PathVariable UUID id) {
    service.delete(id);
  }

  @Operation(summary = "Import entries from CSV", operationId = "importCsv")
  @ApiResponse(responseCode = "200", description = "Import completed")
  @ApiResponse(responseCode = "400", description = "Invalid file")
  @PostMapping("/import")
  public ResponseEntity<ImportResult> uploadCsv(@RequestParam("file") MultipartFile file) {

    if (file.isEmpty()) {
      return ResponseEntity.badRequest().build();
    }

    String filename = file.getOriginalFilename();
    if (filename == null || !filename.endsWith(".csv")) {
      return ResponseEntity.badRequest().build();
    }

    try {
      byte[] fileBytes = file.getBytes();
      String checksum = calculateChecksum(fileBytes);

      try (InputStream inputStream = new ByteArrayInputStream(fileBytes)) {
        ImportResult result = csvImportService.importFromCsvWithBatching(inputStream);
        auditService.writeAuditAsync(
            AuditRecord.success(filename, AuditRecord.ImportType.API, result, checksum));
        return ResponseEntity.ok(result);
      }
    } catch (IOException e) {
      log.error("Failed to process uploaded CSV: {}", e.getMessage());
      auditService.writeAuditAsync(
          AuditRecord.error(filename, AuditRecord.ImportType.API, e.getMessage(), null, null));
      return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
    }
  }

  private String calculateChecksum(byte[] data) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      byte[] hash = digest.digest(data);
      return HexFormat.of().formatHex(hash);
    } catch (NoSuchAlgorithmException e) {
      log.warn("Failed to calculate checksum: {}", e.getMessage());
      return null;
    }
  }
}
