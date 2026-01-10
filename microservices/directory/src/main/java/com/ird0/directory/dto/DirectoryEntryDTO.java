package com.ird0.directory.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DirectoryEntryDTO {

  private UUID id;

  @NotBlank(message = "Name is required")
  private String name;

  @NotBlank(message = "Type is required")
  private String type;

  @NotBlank(message = "Email is required")
  @Email(message = "Invalid email format")
  private String email;

  @NotBlank(message = "Phone is required")
  private String phone;

  private String address;
  private String additionalInfo;
}
