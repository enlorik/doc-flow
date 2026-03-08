package com.docflow.job.controller;

import com.docflow.job.dto.CreateJobRequest;
import com.docflow.job.dto.JobResponse;
import com.docflow.job.service.JobService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/projects/{projectId}/jobs")
public class JobController {

    private final JobService jobService;

    public JobController(JobService jobService) {
        this.jobService = jobService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public JobResponse submit(@AuthenticationPrincipal UserDetails user,
                              @PathVariable UUID projectId,
                              @Valid @RequestBody CreateJobRequest request) {
        return jobService.submit(user.getUsername(), projectId, request);
    }

    @GetMapping
    public List<JobResponse> list(@AuthenticationPrincipal UserDetails user,
                                  @PathVariable UUID projectId) {
        return jobService.listForProject(user.getUsername(), projectId);
    }

    @GetMapping("/{jobId}")
    public JobResponse get(@AuthenticationPrincipal UserDetails user,
                           @PathVariable UUID projectId,
                           @PathVariable UUID jobId) {
        return jobService.get(user.getUsername(), projectId, jobId);
    }

    @PostMapping("/{jobId}/cancel")
    public JobResponse cancel(@AuthenticationPrincipal UserDetails user,
                               @PathVariable UUID projectId,
                               @PathVariable UUID jobId) {
        return jobService.cancel(user.getUsername(), projectId, jobId);
    }
}
