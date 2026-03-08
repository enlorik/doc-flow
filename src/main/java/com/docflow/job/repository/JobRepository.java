package com.docflow.job.repository;

import com.docflow.job.entity.Job;
import com.docflow.job.entity.JobStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface JobRepository extends JpaRepository<Job, UUID> {
    List<Job> findByProjectIdOrderByCreatedAtDesc(UUID projectId);
    Optional<Job> findByProjectIdAndIdempotencyKey(UUID projectId, String idempotencyKey);
    List<Job> findByStatusOrderByNextRunAtAsc(JobStatus status);
}
