package com.docflow.apikey.service;

import com.docflow.apikey.entity.ApiKey;
import com.docflow.apikey.repository.ApiKeyRepository;
import com.docflow.common.exception.NotFoundException;
import com.docflow.project.entity.Project;
import com.docflow.project.service.ProjectService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ApiKeyServiceTest {

    @Mock
    private ApiKeyRepository apiKeyRepository;
    @Mock
    private ProjectService projectService;
    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private ApiKeyService apiKeyService;

    @Test
    void revoke_succeedsWhenKeyBelongsToProject() {
        UUID projectId = UUID.randomUUID();
        UUID keyId = UUID.randomUUID();
        String ownerEmail = "owner@example.com";

        Project project = new Project();
        project.setId(projectId);

        ApiKey key = new ApiKey();
        key.setId(keyId);
        key.setProject(project);
        key.setRevoked(false);

        when(projectService.loadAndVerifyOwnership(ownerEmail, projectId)).thenReturn(project);
        when(apiKeyRepository.findById(keyId)).thenReturn(Optional.of(key));

        apiKeyService.revoke(ownerEmail, projectId, keyId);

        assertThat(key.isRevoked()).isTrue();
    }

    @Test
    void revoke_throwsNotFoundWhenKeyBelongsToDifferentProject() {
        UUID projectId = UUID.randomUUID();
        UUID otherProjectId = UUID.randomUUID();
        UUID keyId = UUID.randomUUID();
        String ownerEmail = "owner@example.com";

        Project project = new Project();
        project.setId(projectId);

        Project otherProject = new Project();
        otherProject.setId(otherProjectId);

        ApiKey key = new ApiKey();
        key.setId(keyId);
        key.setProject(otherProject);

        when(projectService.loadAndVerifyOwnership(ownerEmail, projectId)).thenReturn(project);
        when(apiKeyRepository.findById(keyId)).thenReturn(Optional.of(key));

        assertThatThrownBy(() -> apiKeyService.revoke(ownerEmail, projectId, keyId))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("not found in this project");
    }

    @Test
    void revoke_throwsNotFoundWhenKeyDoesNotExist() {
        UUID projectId = UUID.randomUUID();
        UUID keyId = UUID.randomUUID();
        String ownerEmail = "owner@example.com";

        Project project = new Project();
        project.setId(projectId);

        when(projectService.loadAndVerifyOwnership(ownerEmail, projectId)).thenReturn(project);
        when(apiKeyRepository.findById(keyId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> apiKeyService.revoke(ownerEmail, projectId, keyId))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("API key not found");
    }
}
