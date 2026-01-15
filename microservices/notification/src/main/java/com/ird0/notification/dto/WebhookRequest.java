package com.ird0.notification.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class WebhookRequest {

  @NotBlank(message = "Webhook URL is required")
  private String webhookUrl;

  @NotNull(message = "Payload is required")
  private Map<String, Object> payload;
}
