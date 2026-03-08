package com.docflow.job.entity;

public enum JobStatus {
    QUEUED,
    RUNNING,
    SUCCEEDED,
    FAILED,
    RETRY_SCHEDULED,
    DEAD_LETTER,
    CANCELLED
}
