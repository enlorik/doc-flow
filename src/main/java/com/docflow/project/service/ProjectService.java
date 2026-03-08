package com.docflow.project.service;

import com.docflow.auth.entity.User;
import com.docflow.auth.repository.UserRepository;
import com.docflow.common.exception.ForbiddenException;
import com.docflow.common.exception.NotFoundException;
import com.docflow.project.dto.CreateProjectRequest;
import com.docflow.project.dto.ProjectResponse;
import com.docflow.project.entity.Project;
import com.docflow.project.repository.ProjectRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class ProjectService {

    private final ProjectRepository projectRepository;
    private final UserRepository userRepository;

    public ProjectService(ProjectRepository projectRepository, UserRepository userRepository) {
        this.projectRepository = projectRepository;
        this.userRepository = userRepository;
    }

    @Transactional
    public ProjectResponse create(String ownerEmail, CreateProjectRequest request) {
        User owner = loadUser(ownerEmail);
        Project project = new Project(owner, request.name(), request.description());
        return ProjectResponse.from(projectRepository.save(project));
    }

    @Transactional(readOnly = true)
    public List<ProjectResponse> listForUser(String ownerEmail) {
        User owner = loadUser(ownerEmail);
        return projectRepository.findByOwnerIdOrderByCreatedAtDesc(owner.getId())
                .stream().map(ProjectResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public ProjectResponse get(String ownerEmail, UUID projectId) {
        Project project = loadAndVerifyOwnership(ownerEmail, projectId);
        return ProjectResponse.from(project);
    }

    public Project loadAndVerifyOwnership(String ownerEmail, UUID projectId) {
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new NotFoundException("Project not found"));
        if (!project.getOwner().getEmail().equals(ownerEmail)) {
            throw new ForbiddenException("Access denied");
        }
        return project;
    }

    private User loadUser(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new NotFoundException("User not found"));
    }
}
