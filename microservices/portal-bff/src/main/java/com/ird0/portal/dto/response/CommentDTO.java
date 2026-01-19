package com.ird0.portal.dto.response;

import java.time.Instant;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CommentDTO {

  private UUID id;
  private String content;
  private UUID authorId;
  private String authorType;
  private String authorName;
  private Instant createdAt;
}
