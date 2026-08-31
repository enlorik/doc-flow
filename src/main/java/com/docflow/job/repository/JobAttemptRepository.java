package com.docflow.job.repository;

import com.docflow.job.entity.JobAttempt;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface JobAttemptRepository extends JpaRepository<JobAttempt, UUID> {
    List<JobAttempt> findByJobIdOrderByAttemptNumAsc(UUID jobId);
    Optional<JobAttempt> findFirstByJobIdOrderByAttemptNumDesc(UUID jobId);
}
