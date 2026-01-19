package com.ird0.portal.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CommentRequestDTO {

  @NotBlank(message = "Content is required")
  private String content;

  @NotNull(message = "Author ID is required")
  private UUID authorId;

  @NotBlank(message = "Author type is required")
  private String authorType;
}
