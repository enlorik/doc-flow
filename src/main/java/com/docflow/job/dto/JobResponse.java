package com.docflow.job.dto;

import com.docflow.job.entity.Job;
import com.docflow.job.entity.JobStatus;

import java.time.Instant;
import java.util.UUID;

public record JobResponse(
        UUID id,
        UUID projectId,
        String type,
        JobStatus status,
        String inputJson,
        String resultJson,
        String errorMessage,
        int attemptCount,
        int maxAttempts,
        String idempotencyKey,
        Instant nextRunAt,
        Instant createdAt,
        Instant updatedAt
) {
    public static JobResponse from(Job job) {
        return new JobResponse(
                job.getId(),
                job.getProject().getId(),
                job.getType(),
                job.getStatus(),
                job.getInputJson(),
                job.getResultJson(),
                job.getErrorMessage(),
                job.getAttemptCount(),
                job.getMaxAttempts(),
                job.getIdempotencyKey(),
                job.getNextRunAt(),
                job.getCreatedAt(),
                job.getUpdatedAt()
        );
    }
}
