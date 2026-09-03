package com.ithwx.personalknowledgebase.service;

import com.ithwx.personalknowledgebase.dto.WebPage;
import com.ithwx.personalknowledgebase.entity.DocumentEntity;
import com.ithwx.personalknowledgebase.repository.DocumentRepository;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Locale;

@Service
public class DocumentManagementService {

    private final DocumentParserService documentParserService;
    private final WebContentService webContentService;
    private final KnowledgeIngestionService knowledgeIngestionService;
    private final DocumentRepository documentRepository;

    public DocumentManagementService(
            DocumentParserService documentParserService,
            WebContentService webContentService,
            KnowledgeIngestionService knowledgeIngestionService,
            DocumentRepository documentRepository
    ) {
        this.documentParserService = documentParserService;
        this.webContentService = webContentService;
        this.knowledgeIngestionService = knowledgeIngestionService;
        this.documentRepository = documentRepository;
    }

    public DocumentEntity upload(MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("请选择非空文件");
        }

        String fileName = resolveFileName(file.getOriginalFilename());
        String fileType = resolveFileType(fileName);
        String content = documentParserService.parse(file);

        return knowledgeIngestionService.ingest(
                fileName,
                fileType,
                null,
                content
        );
    }

    public DocumentEntity createNote(String title, String content) {
        return knowledgeIngestionService.ingest(
                title,
                "note",
                null,
                content
        );
    }

    public DocumentEntity createLink(String url, String preferredTitle) {
        WebPage page = webContentService.fetch(url);
        String title = StringUtils.hasText(preferredTitle)
                ? preferredTitle.strip()
                : page.title();

        return knowledgeIngestionService.ingest(
                title,
                "web",
                page.url(),
                page.text()
        );
    }

    public List<DocumentEntity> list() {
        return documentRepository.findAllByOrderByUploadTimeDesc();
    }

    public DocumentEntity update(Long id, String name, String content) {
        DocumentEntity entity = requireDocument(id);
        return knowledgeIngestionService.replace(
                entity,
                name,
                entity.getFileType(),
                entity.getSourceUrl(),
                content
        );
    }

    public DocumentEntity replaceFile(Long id, MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("请选择非空文件");
        }

        DocumentEntity entity = requireDocument(id);
        String fileName = resolveFileName(file.getOriginalFilename());
        String fileType = resolveFileType(fileName);
        String content = documentParserService.parse(file);

        return knowledgeIngestionService.replace(
                entity,
                fileName,
                fileType,
                null,
                content
        );
    }

    public void delete(Long id) {
        DocumentEntity entity = requireDocument(id);
        knowledgeIngestionService.deleteVectors(entity.getId());
        documentRepository.delete(entity);
    }

    private DocumentEntity requireDocument(Long id) {
        return documentRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("资料不存在：" + id));
    }

    private String resolveFileName(String originalFileName) {
        if (!StringUtils.hasText(originalFileName)) {
            throw new IllegalArgumentException("无法识别文件名");
        }

        String normalized = originalFileName.replace('\\', '/');
        String fileName = normalized.substring(normalized.lastIndexOf('/') + 1).strip();
        if (fileName.isEmpty()) {
            throw new IllegalArgumentException("无法识别文件名");
        }
        return fileName;
    }

    private String resolveFileType(String fileName) {
        String extension = StringUtils.getFilenameExtension(fileName);
        if (!StringUtils.hasText(extension)) {
            throw new IllegalArgumentException("无法识别文件格式：" + fileName);
        }
        return extension.toLowerCase(Locale.ROOT);
    }
}
