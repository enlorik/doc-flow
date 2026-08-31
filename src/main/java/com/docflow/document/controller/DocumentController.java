package com.docflow.document.controller;

import com.docflow.document.dto.DocumentResponse;
import com.docflow.document.dto.DocumentSummaryResponse;
import com.docflow.document.service.DocumentService;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/projects/{projectId}/documents")
public class DocumentController {

    private final DocumentService documentService;

    public DocumentController(DocumentService documentService) {
        this.documentService = documentService;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public DocumentResponse upload(@AuthenticationPrincipal UserDetails user,
                                   @PathVariable UUID projectId,
                                   @RequestPart("file") MultipartFile file) {
        return documentService.upload(user.getUsername(), projectId, file);
    }

    @GetMapping
    public List<DocumentSummaryResponse> list(@AuthenticationPrincipal UserDetails user,
                                              @PathVariable UUID projectId) {
        return documentService.list(user.getUsername(), projectId);
    }

    @GetMapping("/{documentId}")
    public DocumentResponse get(@AuthenticationPrincipal UserDetails user,
                                @PathVariable UUID projectId,
                                @PathVariable UUID documentId) {
        return documentService.get(user.getUsername(), projectId, documentId);
    }
}

