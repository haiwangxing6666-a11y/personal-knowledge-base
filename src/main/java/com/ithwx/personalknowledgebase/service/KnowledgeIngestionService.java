package com.ithwx.personalknowledgebase.service;

import com.ithwx.personalknowledgebase.entity.DocumentEntity;
import com.ithwx.personalknowledgebase.repository.DocumentRepository;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class KnowledgeIngestionService {

    private static final String STATUS_PROCESSING = "PROCESSING";
    private static final String STATUS_READY = "READY";
    private static final String STATUS_FAILED = "FAILED";

    private final ChunkingService chunkingService;
    private final DocumentRepository documentRepository;
    private final VectorStore vectorStore;

    public KnowledgeIngestionService(
            ChunkingService chunkingService,
            DocumentRepository documentRepository,
            VectorStore vectorStore
    ) {
        this.chunkingService = chunkingService;
        this.documentRepository = documentRepository;
        this.vectorStore = vectorStore;
    }

    public DocumentEntity ingest(
            String name,
            String sourceType,
            String sourceUrl,
            String content
    ) {
        PreparedContent prepared = prepare(name, sourceType, sourceUrl, content);

        DocumentEntity entity = new DocumentEntity();
        applyPreparedContent(entity, prepared);
        entity = documentRepository.save(entity);

        return storeVectors(entity, prepared.chunks(), false);
    }

    public DocumentEntity replace(
            DocumentEntity entity,
            String name,
            String sourceType,
            String sourceUrl,
            String content
    ) {
        if (entity == null || entity.getId() == null) {
            throw new IllegalArgumentException("待更新资料不能为空");
        }

        PreparedContent prepared = prepare(name, sourceType, sourceUrl, content);
        applyPreparedContent(entity, prepared);
        documentRepository.save(entity);

        return storeVectors(entity, prepared.chunks(), true);
    }

    public void deleteVectors(Long documentId) {
        if (documentId == null) {
            throw new IllegalArgumentException("资料 ID 不能为空");
        }
        vectorStore.delete(documentFilter(documentId));
    }

    private DocumentEntity storeVectors(
            DocumentEntity entity,
            List<String> chunks,
            boolean deleteExisting
    ) {
        try {
            if (deleteExisting) {
                deleteVectors(entity.getId());
            }
            vectorStore.add(toVectorDocuments(entity, chunks));
            entity.setStatus(STATUS_READY);
            entity.setChunkCount(chunks.size());
            return documentRepository.save(entity);
        } catch (RuntimeException exception) {
            entity.setStatus(STATUS_FAILED);
            entity.setChunkCount(0);
            documentRepository.save(entity);
            throw new IllegalStateException("资料入库失败：" + entity.getName(), exception);
        }
    }

    private PreparedContent prepare(
            String name,
            String sourceType,
            String sourceUrl,
            String content
    ) {
        String normalizedName = requireText(name, "资料名称", 255);
        String normalizedType = requireText(sourceType, "资料类型", 32)
                .toLowerCase(Locale.ROOT);
        String normalizedUrl = normalizeOptional(sourceUrl, "来源地址", 2048);

        if (content == null || content.isBlank()) {
            throw new IllegalArgumentException("资料正文不能为空");
        }

        List<String> chunks = chunkingService.chunk(content);
        if (chunks.isEmpty()) {
            throw new IllegalArgumentException("资料正文切分后不能为空");
        }

        return new PreparedContent(
                normalizedName,
                normalizedType,
                normalizedUrl,
                sha256(content),
                chunks
        );
    }

    private void applyPreparedContent(
            DocumentEntity entity,
            PreparedContent prepared
    ) {
        entity.setName(prepared.name());
        entity.setFileType(prepared.sourceType());
        entity.setSourceUrl(prepared.sourceUrl().isEmpty() ? null : prepared.sourceUrl());
        entity.setContentHash(prepared.contentHash());
        entity.setStatus(STATUS_PROCESSING);
        entity.setChunkCount(0);
    }

    private Filter.Expression documentFilter(Long documentId) {
        return new FilterExpressionBuilder()
                .eq("documentId", String.valueOf(documentId))
                .build();
    }

    private List<Document> toVectorDocuments(
            DocumentEntity entity,
            List<String> chunks
    ) {
        List<Document> documents = new ArrayList<>(chunks.size());

        for (int index = 0; index < chunks.size(); index++) {
            Map<String, Object> metadata = new LinkedHashMap<>();
            metadata.put("documentId", String.valueOf(entity.getId()));
            metadata.put("documentName", entity.getName());
            metadata.put("sourceType", entity.getFileType());
            metadata.put("sourceUrl", entity.getSourceUrl() == null ? "" : entity.getSourceUrl());
            metadata.put("chunkIndex", index);

            documents.add(Document.builder()
                    .text(chunks.get(index))
                    .metadata(metadata)
                    .build());
        }

        return List.copyOf(documents);
    }

    private String requireText(String value, String fieldName, int maxLength) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + "不能为空");
        }

        String normalized = value.strip();
        if (normalized.length() > maxLength) {
            throw new IllegalArgumentException(fieldName + "长度不能超过 " + maxLength);
        }
        return normalized;
    }

    private String normalizeOptional(String value, String fieldName, int maxLength) {
        if (value == null || value.isBlank()) {
            return "";
        }

        String normalized = value.strip();
        if (normalized.length() > maxLength) {
            throw new IllegalArgumentException(fieldName + "长度不能超过 " + maxLength);
        }
        return normalized;
    }

    private String sha256(String content) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(
                    digest.digest(content.getBytes(StandardCharsets.UTF_8))
            );
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("当前环境不支持 SHA-256", exception);
        }
    }

    private record PreparedContent(
            String name,
            String sourceType,
            String sourceUrl,
            String contentHash,
            List<String> chunks
    ) {
    }
}
