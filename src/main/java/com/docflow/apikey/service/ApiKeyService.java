package com.docflow.apikey.service;

import com.docflow.apikey.dto.ApiKeyResponse;
import com.docflow.apikey.dto.CreateApiKeyRequest;
import com.docflow.apikey.entity.ApiKey;
import com.docflow.apikey.repository.ApiKeyRepository;
import com.docflow.common.exception.NotFoundException;
import com.docflow.project.entity.Project;
import com.docflow.project.service.ProjectService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

@Service
public class ApiKeyService {

    private static final int RAW_KEY_BYTES = 32;
    private static final String KEY_PREFIX_TAG = "df_";

    private final ApiKeyRepository apiKeyRepository;
    private final ProjectService projectService;
    private final PasswordEncoder passwordEncoder;

    public ApiKeyService(ApiKeyRepository apiKeyRepository,
                         ProjectService projectService,
                         PasswordEncoder passwordEncoder) {
        this.apiKeyRepository = apiKeyRepository;
        this.projectService = projectService;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public ApiKeyResponse create(String ownerEmail, UUID projectId, CreateApiKeyRequest request) {
        Project project = projectService.loadAndVerifyOwnership(ownerEmail, projectId);
        String rawKey = generateRawKey();
        String keyPrefix = rawKey.substring(0, Math.min(10, rawKey.length()));
        String keyHash = passwordEncoder.encode(rawKey);
        ApiKey apiKey = new ApiKey(project, request.name(), keyHash, keyPrefix);
        apiKeyRepository.save(apiKey);
        return ApiKeyResponse.fromWithRawKey(apiKey, rawKey);
    }

    @Transactional(readOnly = true)
    public List<ApiKeyResponse> list(String ownerEmail, UUID projectId) {
        projectService.loadAndVerifyOwnership(ownerEmail, projectId);
        return apiKeyRepository.findByProjectIdOrderByCreatedAtDesc(projectId)
                .stream().map(ApiKeyResponse::from).toList();
    }

    @Transactional
    public void revoke(String ownerEmail, UUID projectId, UUID keyId) {
        projectService.loadAndVerifyOwnership(ownerEmail, projectId);
        ApiKey key = apiKeyRepository.findById(keyId)
                .orElseThrow(() -> new NotFoundException("API key not found"));
        key.setRevoked(true);
    }

    private String generateRawKey() {
        byte[] bytes = new byte[RAW_KEY_BYTES];
        new SecureRandom().nextBytes(bytes);
        return KEY_PREFIX_TAG + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
