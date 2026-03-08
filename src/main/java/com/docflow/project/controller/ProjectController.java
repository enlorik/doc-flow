package com.docflow.project.controller;

import com.docflow.project.dto.CreateProjectRequest;
import com.docflow.project.dto.ProjectResponse;
import com.docflow.project.service.ProjectService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/projects")
public class ProjectController {

    private final ProjectService projectService;

    public ProjectController(ProjectService projectService) {
        this.projectService = projectService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ProjectResponse create(@AuthenticationPrincipal UserDetails user,
                                  @Valid @RequestBody CreateProjectRequest request) {
        return projectService.create(user.getUsername(), request);
    }

    @GetMapping
    public List<ProjectResponse> list(@AuthenticationPrincipal UserDetails user) {
        return projectService.listForUser(user.getUsername());
    }

    @GetMapping("/{id}")
    public ProjectResponse get(@AuthenticationPrincipal UserDetails user,
                               @PathVariable UUID id) {
        return projectService.get(user.getUsername(), id);
    }
}
