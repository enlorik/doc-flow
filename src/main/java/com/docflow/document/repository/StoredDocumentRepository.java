package com.docflow.document.repository;

import com.docflow.document.entity.StoredDocument;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface StoredDocumentRepository extends JpaRepository<StoredDocument, UUID> {
    List<StoredDocument> findByProjectIdOrderByCreatedAtDesc(UUID projectId);
}

