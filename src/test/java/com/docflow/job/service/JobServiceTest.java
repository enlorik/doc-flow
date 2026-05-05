package com.docflow.job.service;

import com.docflow.common.exception.ConflictException;
import com.docflow.job.dto.CreateJobRequest;
import com.docflow.job.dto.JobResponse;
import com.docflow.job.entity.Job;
import com.docflow.job.entity.JobStatus;
import com.docflow.job.repository.JobRepository;
import com.docflow.project.entity.Project;
import com.docflow.project.service.ProjectService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JobServiceTest {

    @Mock
    private JobRepository jobRepository;
    @Mock
    private ProjectService projectService;

    @InjectMocks
    private JobService jobService;

    // ── Idempotency ──────────────────────────────────────────────────────────

    @Test
    void submit_returnsExistingJobOnDuplicateIdempotencyKey() {
        UUID projectId = UUID.randomUUID();
        String ownerEmail = "owner@example.com";
        String idempotencyKey = "idem-key-123";

        Project project = new Project();
        project.setId(projectId);

        Job existingJob = new Job(project, "PDF_CONVERT", "{}", idempotencyKey, 3);
        existingJob.setId(UUID.randomUUID());
        existingJob.setStatus(JobStatus.QUEUED);

        CreateJobRequest request = new CreateJobRequest("PDF_CONVERT", "{}", idempotencyKey, 3);

        when(projectService.loadAndVerifyOwnership(ownerEmail, projectId)).thenReturn(project);
        when(jobRepository.findByProjectIdAndIdempotencyKey(projectId, idempotencyKey))
                .thenReturn(Optional.of(existingJob));

        JobResponse response = jobService.submit(ownerEmail, projectId, request);

        assertThat(response.id()).isEqualTo(existingJob.getId());
        assertThat(response.idempotencyKey()).isEqualTo(idempotencyKey);
        verify(jobRepository, never()).save(any());
    }

    @Test
    void submit_createsNewJobWhenIdempotencyKeyIsAbsent() {
        UUID projectId = UUID.randomUUID();
        String ownerEmail = "owner@example.com";

        Project project = new Project();
        project.setId(projectId);

        CreateJobRequest request = new CreateJobRequest("PDF_CONVERT", "{}", null, 3);

        Job savedJob = new Job(project, "PDF_CONVERT", "{}", null, 3);
        savedJob.setId(UUID.randomUUID());
        savedJob.setStatus(JobStatus.QUEUED);

        when(projectService.loadAndVerifyOwnership(ownerEmail, projectId)).thenReturn(project);
        when(jobRepository.save(any())).thenReturn(savedJob);

        JobResponse response = jobService.submit(ownerEmail, projectId, request);

        assertThat(response.id()).isEqualTo(savedJob.getId());
        verify(jobRepository).save(any());
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "   ", "\t", "\n"})
    void submit_treatsBlankIdempotencyKeyAsNull(String blankKey) {
        UUID projectId = UUID.randomUUID();
        String ownerEmail = "owner@example.com";

        Project project = new Project();
        project.setId(projectId);

        CreateJobRequest request = new CreateJobRequest("PDF_CONVERT", "{}", blankKey, 3);

        Job savedJob = new Job(project, "PDF_CONVERT", "{}", null, 3);
        savedJob.setId(UUID.randomUUID());
        savedJob.setStatus(JobStatus.QUEUED);

        when(projectService.loadAndVerifyOwnership(ownerEmail, projectId)).thenReturn(project);
        when(jobRepository.save(any())).thenReturn(savedJob);

        JobResponse response = jobService.submit(ownerEmail, projectId, request);

        assertThat(response.idempotencyKey()).isNull();
        verify(jobRepository, never()).findByProjectIdAndIdempotencyKey(any(), any());
        verify(jobRepository).save(any());
    }

    @Test
    void submit_returnsExistingJobOnConcurrentDuplicateInsert() {
        UUID projectId = UUID.randomUUID();
        String ownerEmail = "owner@example.com";
        String idempotencyKey = "idem-key-concurrent";

        Project project = new Project();
        project.setId(projectId);

        Job existingJob = new Job(project, "PDF_CONVERT", "{}", idempotencyKey, 3);
        existingJob.setId(UUID.randomUUID());
        existingJob.setStatus(JobStatus.QUEUED);

        CreateJobRequest request = new CreateJobRequest("PDF_CONVERT", "{}", idempotencyKey, 3);

        when(projectService.loadAndVerifyOwnership(ownerEmail, projectId)).thenReturn(project);
        // Pre-check finds nothing (race window), but save throws due to unique constraint violation
        when(jobRepository.findByProjectIdAndIdempotencyKey(projectId, idempotencyKey))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(existingJob));
        when(jobRepository.save(any())).thenThrow(new DataIntegrityViolationException("duplicate key"));

        JobResponse response = jobService.submit(ownerEmail, projectId, request);

        assertThat(response.id()).isEqualTo(existingJob.getId());
        assertThat(response.idempotencyKey()).isEqualTo(idempotencyKey);
    }

    // ── Cancellation ─────────────────────────────────────────────────────────

    @ParameterizedTest
    @EnumSource(value = JobStatus.class, names = {"QUEUED", "RUNNING"})
    void cancel_succeedsForCancellableStatuses(JobStatus status) {
        UUID projectId = UUID.randomUUID();
        UUID jobId = UUID.randomUUID();
        String ownerEmail = "owner@example.com";

        Project project = new Project();
        project.setId(projectId);

        Job job = new Job(project, "PDF_CONVERT", "{}", null, 3);
        job.setId(jobId);
        job.setStatus(status);

        when(projectService.loadAndVerifyOwnership(ownerEmail, projectId)).thenReturn(project);
        when(jobRepository.findById(jobId)).thenReturn(Optional.of(job));

        JobResponse response = jobService.cancel(ownerEmail, projectId, jobId);

        assertThat(response.status()).isEqualTo(JobStatus.CANCELLED);
    }

    @ParameterizedTest
    @EnumSource(value = JobStatus.class, names = {"SUCCEEDED", "DEAD_LETTER", "CANCELLED", "FAILED", "RETRY_SCHEDULED"})
    void cancel_throwsConflictForNonCancellableStatuses(JobStatus status) {
        UUID projectId = UUID.randomUUID();
        UUID jobId = UUID.randomUUID();
        String ownerEmail = "owner@example.com";

        Project project = new Project();
        project.setId(projectId);

        Job job = new Job(project, "PDF_CONVERT", "{}", null, 3);
        job.setId(jobId);
        job.setStatus(status);

        when(projectService.loadAndVerifyOwnership(ownerEmail, projectId)).thenReturn(project);
        when(jobRepository.findById(jobId)).thenReturn(Optional.of(job));

        assertThatThrownBy(() -> jobService.cancel(ownerEmail, projectId, jobId))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("Cannot cancel a job in status: " + status);
    }
}
