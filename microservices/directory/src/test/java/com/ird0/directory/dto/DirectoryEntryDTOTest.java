package com.ird0.directory.dto;

import static org.junit.jupiter.api.Assertions.*;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import java.util.Set;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class DirectoryEntryDTOTest {

  private static Validator validator;

  @BeforeAll
  static void setUp() {
    ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
    validator = factory.getValidator();
  }

  @Test
  void testValidDTO_ShouldPassValidation() {
    DirectoryEntryDTO dto = new DirectoryEntryDTO();
    dto.setName("John Doe");
    dto.setType("individual");
    dto.setEmail("john@example.com");
    dto.setPhone("555-1234");

    Set<ConstraintViolation<DirectoryEntryDTO>> violations = validator.validate(dto);

    assertTrue(violations.isEmpty(), "Valid DTO should have no violations");
  }

  @Test
  void testMissingName_ShouldFailValidation() {
    DirectoryEntryDTO dto = new DirectoryEntryDTO();
    dto.setType("individual");
    dto.setEmail("test@example.com");
    dto.setPhone("555-1234");

    Set<ConstraintViolation<DirectoryEntryDTO>> violations = validator.validate(dto);

    assertFalse(violations.isEmpty(), "Should have validation violations");
    assertTrue(
        violations.stream().anyMatch(v -> v.getPropertyPath().toString().equals("name")),
        "Should have violation for name field");
  }

  @Test
  void testBlankName_ShouldFailValidation() {
    DirectoryEntryDTO dto = new DirectoryEntryDTO();
    dto.setName("   ");
    dto.setType("individual");
    dto.setEmail("test@example.com");
    dto.setPhone("555-1234");

    Set<ConstraintViolation<DirectoryEntryDTO>> violations = validator.validate(dto);

    assertFalse(violations.isEmpty());
    assertTrue(violations.stream().anyMatch(v -> v.getPropertyPath().toString().equals("name")));
  }

  @Test
  void testMissingEmail_ShouldFailValidation() {
    DirectoryEntryDTO dto = new DirectoryEntryDTO();
    dto.setName("John Doe");
    dto.setType("individual");
    dto.setPhone("555-1234");

    Set<ConstraintViolation<DirectoryEntryDTO>> violations = validator.validate(dto);

    assertFalse(violations.isEmpty());
    assertTrue(violations.stream().anyMatch(v -> v.getPropertyPath().toString().equals("email")));
  }

  @Test
  void testInvalidEmailFormat_ShouldFailValidation() {
    DirectoryEntryDTO dto = new DirectoryEntryDTO();
    dto.setName("John Doe");
    dto.setType("individual");
    dto.setEmail("not-an-email");
    dto.setPhone("555-1234");

    Set<ConstraintViolation<DirectoryEntryDTO>> violations = validator.validate(dto);

    assertFalse(violations.isEmpty());
    assertTrue(
        violations.stream()
            .anyMatch(
                v ->
                    v.getPropertyPath().toString().equals("email")
                        && v.getMessage().contains("Invalid email format")),
        "Should have email format violation");
  }

  @Test
  void testMissingPhone_ShouldFailValidation() {
    DirectoryEntryDTO dto = new DirectoryEntryDTO();
    dto.setName("John Doe");
    dto.setType("individual");
    dto.setEmail("john@example.com");

    Set<ConstraintViolation<DirectoryEntryDTO>> violations = validator.validate(dto);

    assertFalse(violations.isEmpty());
    assertTrue(violations.stream().anyMatch(v -> v.getPropertyPath().toString().equals("phone")));
  }

  @Test
  void testOptionalFields_CanBeNull() {
    DirectoryEntryDTO dto = new DirectoryEntryDTO();
    dto.setName("John Doe");
    dto.setType("individual");
    dto.setEmail("john@example.com");
    dto.setPhone("555-1234");
    dto.setAddress(null);
    dto.setAdditionalInfo(null);

    Set<ConstraintViolation<DirectoryEntryDTO>> violations = validator.validate(dto);

    assertTrue(violations.isEmpty(), "Optional fields (address, additionalInfo) can be null");
  }

  @Test
  void testAllFieldsProvided_ShouldPassValidation() {
    DirectoryEntryDTO dto = new DirectoryEntryDTO();
    dto.setName("John Doe");
    dto.setType("individual");
    dto.setEmail("john@example.com");
    dto.setPhone("555-1234");
    dto.setAddress("123 Main St");
    dto.setAdditionalInfo("Test account");

    Set<ConstraintViolation<DirectoryEntryDTO>> violations = validator.validate(dto);

    assertTrue(violations.isEmpty());
  }
}
