package com.docflow.job.entity;

import com.docflow.audit.BaseEntity;
import com.docflow.project.entity.Project;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "jobs")
@Getter
@Setter
@NoArgsConstructor
public class Job extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;

    @Column(nullable = false)
    private String type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private JobStatus status = JobStatus.QUEUED;

    @Column(name = "input_json", columnDefinition = "TEXT")
    private String inputJson;

    @Column(name = "result_json", columnDefinition = "TEXT")
    private String resultJson;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "attempt_count", nullable = false)
    private int attemptCount = 0;

    @Column(name = "max_attempts", nullable = false)
    private int maxAttempts = 3;

    @Column(name = "idempotency_key")
    private String idempotencyKey;

    @Column(name = "next_run_at")
    private Instant nextRunAt;

    public Job(Project project, String type, String inputJson, String idempotencyKey, int maxAttempts) {
        this.project = project;
        this.type = type;
        this.inputJson = inputJson;
        this.idempotencyKey = idempotencyKey;
        this.maxAttempts = maxAttempts;
    }
}
