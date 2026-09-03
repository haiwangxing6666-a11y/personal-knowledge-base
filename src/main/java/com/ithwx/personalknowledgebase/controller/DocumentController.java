package com.ithwx.personalknowledgebase.controller;

import com.ithwx.personalknowledgebase.dto.DocumentResponse;
import com.ithwx.personalknowledgebase.dto.LinkCreateRequest;
import com.ithwx.personalknowledgebase.dto.NoteCreateRequest;
import com.ithwx.personalknowledgebase.service.DocumentManagementService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping("/api/documents")
public class DocumentController {

    private final DocumentManagementService documentManagementService;

    public DocumentController(DocumentManagementService documentManagementService) {
        this.documentManagementService = documentManagementService;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public DocumentResponse upload(@RequestPart("file") MultipartFile file) throws IOException {
        return DocumentResponse.from(documentManagementService.upload(file));
    }

    @PostMapping("/notes")
    @ResponseStatus(HttpStatus.CREATED)
    public DocumentResponse createNote(@Valid @RequestBody NoteCreateRequest request) {
        return DocumentResponse.from(
                documentManagementService.createNote(request.title(), request.content())
        );
    }

    @PostMapping("/links")
    @ResponseStatus(HttpStatus.CREATED)
    public DocumentResponse createLink(@Valid @RequestBody LinkCreateRequest request) {
        return DocumentResponse.from(
                documentManagementService.createLink(request.url(), request.title())
        );
    }

    @GetMapping
    public List<DocumentResponse> list() {
        return documentManagementService.list().stream()
                .map(DocumentResponse::from)
                .toList();
    }
}
