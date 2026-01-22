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
    DirectoryEntryDTO dto =
        new DirectoryEntryDTO(
            null, "John Doe", "individual", "john@example.com", "555-1234", null, null, null);

    Set<ConstraintViolation<DirectoryEntryDTO>> violations = validator.validate(dto);

    assertTrue(violations.isEmpty(), "Valid DTO should have no violations");
  }

  @Test
  void testMissingName_ShouldFailValidation() {
    DirectoryEntryDTO dto =
        new DirectoryEntryDTO(
            null, null, "individual", "test@example.com", "555-1234", null, null, null);

    Set<ConstraintViolation<DirectoryEntryDTO>> violations = validator.validate(dto);

    assertFalse(violations.isEmpty(), "Should have validation violations");
    assertTrue(
        violations.stream().anyMatch(v -> v.getPropertyPath().toString().equals("name")),
        "Should have violation for name field");
  }

  @Test
  void testBlankName_ShouldFailValidation() {
    DirectoryEntryDTO dto =
        new DirectoryEntryDTO(
            null, "   ", "individual", "test@example.com", "555-1234", null, null, null);

    Set<ConstraintViolation<DirectoryEntryDTO>> violations = validator.validate(dto);

    assertFalse(violations.isEmpty());
    assertTrue(violations.stream().anyMatch(v -> v.getPropertyPath().toString().equals("name")));
  }

  @Test
  void testMissingEmail_ShouldFailValidation() {
    DirectoryEntryDTO dto =
        new DirectoryEntryDTO(null, "John Doe", "individual", null, "555-1234", null, null, null);

    Set<ConstraintViolation<DirectoryEntryDTO>> violations = validator.validate(dto);

    assertFalse(violations.isEmpty());
    assertTrue(violations.stream().anyMatch(v -> v.getPropertyPath().toString().equals("email")));
  }

  @Test
  void testInvalidEmailFormat_ShouldFailValidation() {
    DirectoryEntryDTO dto =
        new DirectoryEntryDTO(
            null, "John Doe", "individual", "not-an-email", "555-1234", null, null, null);

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
    DirectoryEntryDTO dto =
        new DirectoryEntryDTO(
            null, "John Doe", "individual", "john@example.com", null, null, null, null);

    Set<ConstraintViolation<DirectoryEntryDTO>> violations = validator.validate(dto);

    assertFalse(violations.isEmpty());
    assertTrue(violations.stream().anyMatch(v -> v.getPropertyPath().toString().equals("phone")));
  }

  @Test
  void testOptionalFields_CanBeNull() {
    DirectoryEntryDTO dto =
        new DirectoryEntryDTO(
            null, "John Doe", "individual", "john@example.com", "555-1234", null, null, null);

    Set<ConstraintViolation<DirectoryEntryDTO>> violations = validator.validate(dto);

    assertTrue(violations.isEmpty(), "Optional fields (address, additionalInfo) can be null");
  }

  @Test
  void testAllFieldsProvided_ShouldPassValidation() {
    DirectoryEntryDTO dto =
        new DirectoryEntryDTO(
            null,
            "John Doe",
            "individual",
            "john@example.com",
            "555-1234",
            "123 Main St",
            "Test account",
            null);

    Set<ConstraintViolation<DirectoryEntryDTO>> violations = validator.validate(dto);

    assertTrue(violations.isEmpty());
  }
}
