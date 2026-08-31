package com.docflow.job.repository;

import com.docflow.job.entity.Job;
import com.docflow.job.entity.JobStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface JobRepository extends JpaRepository<Job, UUID> {
    List<Job> findByProjectIdOrderByCreatedAtDesc(UUID projectId);
    Optional<Job> findByProjectIdAndIdempotencyKey(UUID projectId, String idempotencyKey);
    List<Job> findByStatusOrderByNextRunAtAsc(JobStatus status);

    @Query(value = """
            SELECT * FROM jobs
            WHERE status = 'QUEUED'
              AND (next_run_at IS NULL OR next_run_at <= now())
            ORDER BY created_at ASC
            FOR UPDATE SKIP LOCKED
            LIMIT 1
            """, nativeQuery = true)
    Optional<Job> findNextQueuedForUpdate();

    @Query(value = """
            SELECT * FROM jobs
            WHERE status = 'RUNNING'
            ORDER BY updated_at ASC
            FOR UPDATE SKIP LOCKED
            LIMIT 1
            """, nativeQuery = true)
    Optional<Job> findNextRunningForUpdate();
}
