package com.docflow.job.service;

import com.docflow.job.entity.Job;
import com.docflow.job.entity.JobAttempt;
import com.docflow.job.entity.JobStatus;
import com.docflow.job.repository.JobAttemptRepository;
import com.docflow.job.repository.JobRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

@Service
public class JobProcessorService {

    private static final String TEXT_ANALYZE = "TEXT_ANALYZE";

    private final JobRepository jobRepository;
    private final JobAttemptRepository jobAttemptRepository;
    private final ObjectMapper objectMapper;

    public JobProcessorService(JobRepository jobRepository,
                               JobAttemptRepository jobAttemptRepository,
                               ObjectMapper objectMapper) {
        this.jobRepository = jobRepository;
        this.jobAttemptRepository = jobAttemptRepository;
        this.objectMapper = objectMapper;
    }

    @Scheduled(fixedDelayString = "${docflow.worker.poll-ms:1000}")
    @Transactional
    public void processNextQueuedJob() {
        jobRepository.findNextQueuedForUpdate().ifPresent(this::process);
    }

    private void process(Job job) {
        int attemptNumber = job.getAttemptCount() + 1;
        JobAttempt attempt = new JobAttempt(job, attemptNumber);
        jobAttemptRepository.save(attempt);

        job.setAttemptCount(attemptNumber);
        job.setStatus(JobStatus.RUNNING);
        job.setErrorMessage(null);

        try {
            String result = switch (job.getType().toUpperCase(Locale.ROOT)) {
                case TEXT_ANALYZE -> analyzeText(job.getInputJson());
                default -> throw new IllegalArgumentException(
                        "Unsupported job type: " + job.getType() + ". Supported type: " + TEXT_ANALYZE);
            };

            job.setResultJson(result);
            job.setStatus(JobStatus.SUCCEEDED);
            attempt.setOutcome(JobStatus.SUCCEEDED.name());
        } catch (Exception ex) {
            String message = ex.getMessage() == null ? "Document processing failed" : ex.getMessage();
            job.setErrorMessage(message);
            job.setStatus(attemptNumber >= job.getMaxAttempts()
                    ? JobStatus.DEAD_LETTER
                    : JobStatus.FAILED);
            attempt.setOutcome(job.getStatus().name());
            attempt.setError(message);
        } finally {
            attempt.setFinishedAt(Instant.now());
        }
    }

    private String analyzeText(String inputJson) throws JsonProcessingException {
        if (inputJson == null || inputJson.isBlank()) {
            throw new IllegalArgumentException("inputJson must contain a text field");
        }

        JsonNode input = objectMapper.readTree(inputJson);
        JsonNode textNode = input.get("text");
        if (textNode == null || !textNode.isTextual() || textNode.asText().isBlank()) {
            throw new IllegalArgumentException("The text field must contain a document");
        }

        String text = textNode.asText();
        String trimmed = text.trim();
        String compact = text.replaceAll("\\s", "");
        int words = trimmed.isEmpty() ? 0 : trimmed.split("\\s+").length;
        int characters = text.codePointCount(0, text.length());
        int charactersWithoutSpaces = compact.codePointCount(0, compact.length());
        long lines = text.lines().count();
        int sentences = trimmed.isEmpty() ? 0 : trimmed.split("(?<=[.!?])(?:\\s+|$)").length;
        int paragraphs = trimmed.isEmpty() ? 0 : trimmed.split("(?:\\r?\\n){2,}").length;
        int readingTimeSeconds = words == 0 ? 0 : Math.max(1, (int) Math.ceil(words * 60.0 / 200.0));

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("type", TEXT_ANALYZE);
        result.put("words", words);
        result.put("characters", characters);
        result.put("charactersWithoutSpaces", charactersWithoutSpaces);
        result.put("lines", lines);
        result.put("sentences", sentences);
        result.put("paragraphs", paragraphs);
        result.put("estimatedReadingTimeSeconds", readingTimeSeconds);
        result.put("processedAt", Instant.now());
        return objectMapper.writeValueAsString(result);
    }
}

