package com.docflow.job.entity;

import com.docflow.audit.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "job_attempts")
@Getter
@Setter
@NoArgsConstructor
public class JobAttempt extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "job_id", nullable = false)
    private Job job;

    @Column(name = "attempt_num", nullable = false)
    private int attemptNum;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "finished_at")
    private Instant finishedAt;

    @Column(name = "outcome", length = 50)
    private String outcome;

    @Column(name = "error", columnDefinition = "TEXT")
    private String error;

    public JobAttempt(Job job, int attemptNum) {
        this.job = job;
        this.attemptNum = attemptNum;
        this.startedAt = Instant.now();
    }
}
