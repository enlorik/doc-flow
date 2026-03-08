package com.docflow.apikey.dto;

import com.docflow.apikey.entity.ApiKey;

import java.time.Instant;
import java.util.UUID;

public record ApiKeyResponse(
        UUID id,
        UUID projectId,
        String name,
        String keyPrefix,
        boolean revoked,
        Instant createdAt,
        // rawKey is only populated on creation, null on subsequent reads
        String rawKey
) {
    public static ApiKeyResponse from(ApiKey key) {
        return new ApiKeyResponse(
                key.getId(),
                key.getProject().getId(),
                key.getName(),
                key.getKeyPrefix(),
                key.isRevoked(),
                key.getCreatedAt(),
                null
        );
    }

    public static ApiKeyResponse fromWithRawKey(ApiKey key, String rawKey) {
        return new ApiKeyResponse(
                key.getId(),
                key.getProject().getId(),
                key.getName(),
                key.getKeyPrefix(),
                key.isRevoked(),
                key.getCreatedAt(),
                rawKey
        );
    }
}
