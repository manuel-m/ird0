package com.ird0.incident.dto;

import com.ird0.incident.model.Comment.AuthorType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CommentRequest {

  @NotNull(message = "Author ID is required")
  private UUID authorId;

  @NotNull(message = "Author type is required")
  private AuthorType authorType;

  @NotBlank(message = "Content is required")
  private String content;
}
