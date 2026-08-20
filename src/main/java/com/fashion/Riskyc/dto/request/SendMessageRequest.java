package com.fashion.Riskyc.dto.request;

import com.fashion.Riskyc.entity.MessageSender;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record SendMessageRequest(
        @NotNull UUID conversationId,
        @NotNull MessageSender sender,
        @NotBlank String text
) {
}
