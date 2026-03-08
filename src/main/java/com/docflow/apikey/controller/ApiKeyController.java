package com.docflow.apikey.controller;

import com.docflow.apikey.dto.ApiKeyResponse;
import com.docflow.apikey.dto.CreateApiKeyRequest;
import com.docflow.apikey.service.ApiKeyService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/projects/{projectId}/keys")
public class ApiKeyController {

    private final ApiKeyService apiKeyService;

    public ApiKeyController(ApiKeyService apiKeyService) {
        this.apiKeyService = apiKeyService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiKeyResponse create(@AuthenticationPrincipal UserDetails user,
                                 @PathVariable UUID projectId,
                                 @Valid @RequestBody CreateApiKeyRequest request) {
        return apiKeyService.create(user.getUsername(), projectId, request);
    }

    @GetMapping
    public List<ApiKeyResponse> list(@AuthenticationPrincipal UserDetails user,
                                     @PathVariable UUID projectId) {
        return apiKeyService.list(user.getUsername(), projectId);
    }

    @DeleteMapping("/{keyId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void revoke(@AuthenticationPrincipal UserDetails user,
                       @PathVariable UUID projectId,
                       @PathVariable UUID keyId) {
        apiKeyService.revoke(user.getUsername(), projectId, keyId);
    }
}
