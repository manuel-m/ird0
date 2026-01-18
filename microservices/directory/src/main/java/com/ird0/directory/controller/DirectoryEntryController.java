package com.ird0.directory.controller;

import com.ird0.directory.dto.AuditRecord;
import com.ird0.directory.dto.DirectoryEntryDTO;
import com.ird0.directory.dto.ImportResult;
import com.ird0.directory.mapper.DirectoryEntryMapper;
import com.ird0.directory.model.DirectoryEntry;
import com.ird0.directory.service.CsvImportService;
import com.ird0.directory.service.DirectoryEntryService;
import com.ird0.directory.service.ImportAuditService;
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
public class DirectoryEntryController {

  private final DirectoryEntryService service;
  private final DirectoryEntryMapper mapper;
  private final CsvImportService csvImportService;
  private final ImportAuditService auditService;

  @GetMapping
  public List<DirectoryEntryDTO> getAll() {
    List<DirectoryEntry> entities = service.getAll();
    return mapper.toDTOList(entities);
  }

  @GetMapping("/{id}")
  public DirectoryEntryDTO getOne(@PathVariable UUID id) {
    DirectoryEntry entity = service.getById(id);
    return mapper.toDTO(entity);
  }

  @PostMapping
  public DirectoryEntryDTO create(@Valid @RequestBody DirectoryEntryDTO dto) {
    DirectoryEntry entity = mapper.toEntity(dto);
    DirectoryEntry saved = service.create(entity);
    return mapper.toDTO(saved);
  }

  @PutMapping("/{id}")
  public DirectoryEntryDTO update(
      @PathVariable UUID id, @Valid @RequestBody DirectoryEntryDTO dto) {
    DirectoryEntry existing = service.getById(id);
    mapper.updateEntityFromDTO(dto, existing);
    DirectoryEntry updated = service.update(id, existing);
    return mapper.toDTO(updated);
  }

  @DeleteMapping("/{id}")
  public void delete(@PathVariable UUID id) {
    service.delete(id);
  }

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
