package com.docflow.document.service;

import com.docflow.common.exception.BadRequestException;
import com.docflow.common.exception.NotFoundException;
import com.docflow.document.dto.DocumentResponse;
import com.docflow.document.dto.DocumentSummaryResponse;
import com.docflow.document.entity.StoredDocument;
import com.docflow.document.repository.StoredDocumentRepository;
import com.docflow.project.entity.Project;
import com.docflow.project.service.ProjectService;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
public class DocumentService {

    private static final long MAX_FILE_BYTES = 10L * 1024L * 1024L;
    private static final int MAX_PDF_PAGES = 250;
    private static final int MAX_EXTRACTED_CHARS = 2_000_000;

    private final StoredDocumentRepository documentRepository;
    private final ProjectService projectService;

    public DocumentService(StoredDocumentRepository documentRepository, ProjectService projectService) {
        this.documentRepository = documentRepository;
        this.projectService = projectService;
    }

    @Transactional
    public DocumentResponse upload(String ownerEmail, UUID projectId, MultipartFile file) {
        Project project = projectService.loadAndVerifyOwnership(ownerEmail, projectId);
        validateUpload(file);

        String fileName = cleanFileName(file.getOriginalFilename());
        String extension = extensionOf(fileName);

        byte[] bytes;
        String extractedText;
        try {
            bytes = file.getBytes();
            extractedText = normalizeText(extractText(bytes, extension));
            if (extractedText.isBlank()) {
                throw new BadRequestException("No readable text was found in this document");
            }
            if (extractedText.length() > MAX_EXTRACTED_CHARS) {
                throw new BadRequestException("The extracted document is too large to process safely");
            }
        } catch (BadRequestException ex) {
            throw ex;
        } catch (IOException | RuntimeException ex) {
            throw new BadRequestException("This " + extension.toUpperCase(Locale.ROOT)
                    + " file could not be read. It may be damaged, encrypted, or unsupported");
        }

        StoredDocument document = new StoredDocument(
                project,
                fileName,
                contentTypeFor(extension),
                bytes.length,
                sha256(bytes),
                extractedText
        );
        return DocumentResponse.from(documentRepository.save(document));
    }

    @Transactional(readOnly = true)
    public List<DocumentSummaryResponse> list(String ownerEmail, UUID projectId) {
        projectService.loadAndVerifyOwnership(ownerEmail, projectId);
        return documentRepository.findByProjectIdOrderByCreatedAtDesc(projectId)
                .stream()
                .map(DocumentSummaryResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public DocumentResponse get(String ownerEmail, UUID projectId, UUID documentId) {
        projectService.loadAndVerifyOwnership(ownerEmail, projectId);
        StoredDocument document = documentRepository.findById(documentId)
                .orElseThrow(() -> new NotFoundException("Document not found"));
        if (!document.getProject().getId().equals(projectId)) {
            throw new NotFoundException("Document not found in this project");
        }
        return DocumentResponse.from(document);
    }

    private void validateUpload(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BadRequestException("Choose a PDF, DOCX, or TXT file to upload");
        }
        if (file.getSize() > MAX_FILE_BYTES) {
            throw new BadRequestException("Files must be 10 MB or smaller");
        }
        String extension = extensionOf(cleanFileName(file.getOriginalFilename()));
        if (!List.of("pdf", "docx", "txt").contains(extension)) {
            throw new BadRequestException("Supported file types are PDF, DOCX, and TXT");
        }
    }

    private String extractText(byte[] bytes, String extension) throws IOException {
        return switch (extension) {
            case "pdf" -> extractPdf(bytes);
            case "docx" -> extractDocx(bytes);
            case "txt" -> new String(bytes, StandardCharsets.UTF_8);
            default -> throw new BadRequestException("Unsupported document type");
        };
    }

    private String extractPdf(byte[] bytes) throws IOException {
        try (PDDocument document = Loader.loadPDF(bytes)) {
            if (document.getNumberOfPages() > MAX_PDF_PAGES) {
                throw new BadRequestException("PDF files may contain at most " + MAX_PDF_PAGES + " pages");
            }
            return new PDFTextStripper().getText(document);
        }
    }

    private String extractDocx(byte[] bytes) throws IOException {
        try (XWPFDocument document = new XWPFDocument(new ByteArrayInputStream(bytes));
             XWPFWordExtractor extractor = new XWPFWordExtractor(document)) {
            return extractor.getText();
        }
    }

    private String cleanFileName(String originalName) {
        String name = originalName == null ? "document" : originalName.replace('\\', '/');
        name = name.substring(name.lastIndexOf('/') + 1).strip();
        name = name.replaceAll("[\\p{Cntrl}]", "");
        if (name.isBlank()) name = "document";
        if (name.length() > 255) name = name.substring(name.length() - 255);
        return name;
    }

    private String extensionOf(String fileName) {
        int dot = fileName.lastIndexOf('.');
        return dot < 0 ? "" : fileName.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    private String contentTypeFor(String extension) {
        return switch (extension) {
            case "pdf" -> "application/pdf";
            case "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
            case "txt" -> "text/plain";
            default -> "application/octet-stream";
        };
    }

    private String normalizeText(String text) {
        return text.replace("\u0000", " ")
                .replace("\r\n", "\n")
                .replace('\r', '\n')
                .strip();
    }

    private String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is unavailable", ex);
        }
    }
}

