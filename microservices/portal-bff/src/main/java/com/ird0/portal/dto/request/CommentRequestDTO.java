package com.ird0.portal.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record CommentRequestDTO(
    @NotBlank(message = "Content is required") String content,
    @NotNull(message = "Author ID is required") UUID authorId,
    @NotBlank(message = "Author type is required") String authorType) {}
