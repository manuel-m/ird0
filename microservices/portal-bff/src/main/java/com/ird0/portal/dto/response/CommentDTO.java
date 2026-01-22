package com.ird0.portal.dto.response;

import java.time.Instant;
import java.util.UUID;

public record CommentDTO(
    UUID id,
    String content,
    UUID authorId,
    String authorType,
    String authorName,
    Instant createdAt) {}
