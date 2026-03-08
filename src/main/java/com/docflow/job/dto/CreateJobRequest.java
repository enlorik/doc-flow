package com.docflow.job.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateJobRequest(
        @NotBlank @Size(max = 255) String type,
        String inputJson,
        @Size(max = 255) String idempotencyKey,
        int maxAttempts
) {
    public CreateJobRequest {
        if (maxAttempts <= 0) maxAttempts = 3;
    }
}
