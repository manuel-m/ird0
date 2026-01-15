package com.ird0.incident.dto;

import com.ird0.incident.model.Comment.AuthorType;
import java.time.Instant;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CommentResponse {

  private UUID id;
  private UUID authorId;
  private AuthorType authorType;
  private String content;
  private Instant createdAt;
}
