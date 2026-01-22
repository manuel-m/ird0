package com.ird0.incident.dto;

import com.ird0.incident.model.Comment.AuthorType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record CommentRequest(
    @NotNull(message = "Author ID is required") UUID authorId,
    @NotNull(message = "Author type is required") AuthorType authorType,
    @NotBlank(message = "Content is required") String content) {}
