package com.docflow.apikey.repository;

import com.docflow.apikey.entity.ApiKey;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ApiKeyRepository extends JpaRepository<ApiKey, UUID> {
    List<ApiKey> findByProjectIdOrderByCreatedAtDesc(UUID projectId);
}
