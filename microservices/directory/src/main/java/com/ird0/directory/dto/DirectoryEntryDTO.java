package com.ird0.directory.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import java.util.UUID;

public record DirectoryEntryDTO(
    UUID id,
    @NotBlank(message = "Name is required") String name,
    @NotBlank(message = "Type is required") String type,
    @NotBlank(message = "Email is required") @Email(message = "Invalid email format") String email,
    @NotBlank(message = "Phone is required") String phone,
    String address,
    String additionalInfo,
    String webhookUrl) {}
