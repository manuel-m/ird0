package com.ird0.incident.dto;

import com.ird0.incident.model.Comment.AuthorType;
import java.time.Instant;
import java.util.UUID;

public record CommentResponse(
    UUID id, UUID authorId, AuthorType authorType, String content, Instant createdAt) {}
