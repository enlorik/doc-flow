package com.docflow.project.repository;

import com.docflow.project.entity.Project;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ProjectRepository extends JpaRepository<Project, UUID> {
    List<Project> findByOwnerIdOrderByCreatedAtDesc(UUID ownerId);
}
