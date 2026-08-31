package com.docflow.document.dto;

import com.docflow.document.entity.StoredDocument;

import java.time.Instant;
import java.util.UUID;

public record DocumentResponse(
        UUID id,
        UUID projectId,
        String originalName,
        String contentType,
        long sizeBytes,
        String contentHash,
        int extractedCharacters,
        String extractedText,
        Instant createdAt
) {
    public static DocumentResponse from(StoredDocument document) {
        return new DocumentResponse(
                document.getId(),
                document.getProject().getId(),
                document.getOriginalName(),
                document.getContentType(),
                document.getSizeBytes(),
                document.getContentHash(),
                document.getExtractedText().codePointCount(0, document.getExtractedText().length()),
                document.getExtractedText(),
                document.getCreatedAt()
        );
    }
}

