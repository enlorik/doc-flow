package com.docflow.job.service;

import com.docflow.common.exception.ConflictException;
import com.docflow.common.exception.NotFoundException;
import com.docflow.job.dto.CreateJobRequest;
import com.docflow.job.dto.JobResponse;
import com.docflow.job.entity.Job;
import com.docflow.job.entity.JobStatus;
import com.docflow.job.repository.JobRepository;
import com.docflow.project.entity.Project;
import com.docflow.project.service.ProjectService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class JobService {

    private final JobRepository jobRepository;
    private final ProjectService projectService;

    public JobService(JobRepository jobRepository, ProjectService projectService) {
        this.jobRepository = jobRepository;
        this.projectService = projectService;
    }

    @Transactional
    public JobResponse submit(String ownerEmail, UUID projectId, CreateJobRequest request) {
        Project project = projectService.loadAndVerifyOwnership(ownerEmail, projectId);

        if (request.idempotencyKey() != null && !request.idempotencyKey().isBlank()) {
            jobRepository.findByProjectIdAndIdempotencyKey(projectId, request.idempotencyKey())
                    .ifPresent(existing -> {
                        throw new ConflictException("Job with this idempotency key already exists");
                    });
        }

        Job job = new Job(project, request.type(), request.inputJson(),
                request.idempotencyKey(), request.maxAttempts());
        job.setStatus(JobStatus.QUEUED);
        return JobResponse.from(jobRepository.save(job));
    }

    @Transactional(readOnly = true)
    public List<JobResponse> listForProject(String ownerEmail, UUID projectId) {
        projectService.loadAndVerifyOwnership(ownerEmail, projectId);
        return jobRepository.findByProjectIdOrderByCreatedAtDesc(projectId)
                .stream().map(JobResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public JobResponse get(String ownerEmail, UUID projectId, UUID jobId) {
        projectService.loadAndVerifyOwnership(ownerEmail, projectId);
        Job job = jobRepository.findById(jobId)
                .orElseThrow(() -> new NotFoundException("Job not found"));
        if (!job.getProject().getId().equals(projectId)) {
            throw new NotFoundException("Job not found in this project");
        }
        return JobResponse.from(job);
    }

    @Transactional
    public JobResponse cancel(String ownerEmail, UUID projectId, UUID jobId) {
        projectService.loadAndVerifyOwnership(ownerEmail, projectId);
        Job job = jobRepository.findById(jobId)
                .orElseThrow(() -> new NotFoundException("Job not found"));
        if (!job.getProject().getId().equals(projectId)) {
            throw new NotFoundException("Job not found in this project");
        }
        if (job.getStatus() == JobStatus.SUCCEEDED || job.getStatus() == JobStatus.DEAD_LETTER) {
            throw new ConflictException("Cannot cancel a job in status: " + job.getStatus());
        }
        job.setStatus(JobStatus.CANCELLED);
        return JobResponse.from(job);
    }
}
