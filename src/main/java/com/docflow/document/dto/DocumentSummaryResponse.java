package com.docflow.document.dto;

import com.docflow.document.entity.StoredDocument;

import java.time.Instant;
import java.util.UUID;

public record DocumentSummaryResponse(
        UUID id,
        UUID projectId,
        String originalName,
        String contentType,
        long sizeBytes,
        int extractedCharacters,
        String preview,
        Instant createdAt
) {
    public static DocumentSummaryResponse from(StoredDocument document) {
        String text = document.getExtractedText();
        String preview = text.length() <= 280 ? text : text.substring(0, 280) + "…";
        return new DocumentSummaryResponse(
                document.getId(),
                document.getProject().getId(),
                document.getOriginalName(),
                document.getContentType(),
                document.getSizeBytes(),
                text.codePointCount(0, text.length()),
                preview,
                document.getCreatedAt()
        );
    }
}

