package com.ithwx.personalknowledgebase.service;

import com.ithwx.personalknowledgebase.entity.DocumentEntity;
import com.ithwx.personalknowledgebase.repository.DocumentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.Filter;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class KnowledgeIngestionServiceTest {

    @Mock
    private ChunkingService chunkingService;

    @Mock
    private DocumentRepository documentRepository;

    @Mock
    private VectorStore vectorStore;

    private KnowledgeIngestionService service;

    @BeforeEach
    void setUp() {
        service = new KnowledgeIngestionService(
                chunkingService,
                documentRepository,
                vectorStore
        );
    }

    @Test
    void shouldRejectBlankContentBeforeSaving() {
        assertThrows(
                IllegalArgumentException.class,
                () -> service.ingest("空资料", "note", null, "  \n ")
        );

        verifyNoInteractions(chunkingService, documentRepository, vectorStore);
    }

    @Test
    void shouldSaveDocumentAndVectorChunksWithSourceMetadata() {
        stubRepositorySave();
        when(chunkingService.chunk("第一段\n\n第二段"))
                .thenReturn(List.of("第一段", "第二段"));

        DocumentEntity result = service.ingest(
                "Spring 学习笔记",
                "NOTE",
                "https://example.com/note",
                "第一段\n\n第二段"
        );

        assertEquals(42L, result.getId());
        assertEquals("Spring 学习笔记", result.getName());
        assertEquals("note", result.getFileType());
        assertEquals("https://example.com/note", result.getSourceUrl());
        assertEquals("READY", result.getStatus());
        assertEquals(2, result.getChunkCount());
        assertNotNull(result.getContentHash());
        assertTrue(result.getContentHash().matches("[0-9a-f]{64}"));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Document>> documentsCaptor =
                ArgumentCaptor.forClass(List.class);
        verify(vectorStore).add(documentsCaptor.capture());

        List<Document> documents = documentsCaptor.getValue();
        assertEquals(2, documents.size());
        assertEquals("第一段", documents.get(0).getText());
        assertEquals("第二段", documents.get(1).getText());
        assertEquals("42", documents.get(0).getMetadata().get("documentId"));
        assertEquals("Spring 学习笔记", documents.get(0).getMetadata().get("documentName"));
        assertEquals("note", documents.get(0).getMetadata().get("sourceType"));
        assertEquals("https://example.com/note", documents.get(0).getMetadata().get("sourceUrl"));
        assertEquals(0, documents.get(0).getMetadata().get("chunkIndex"));
        assertEquals(1, documents.get(1).getMetadata().get("chunkIndex"));
        verify(documentRepository, times(2)).save(any(DocumentEntity.class));
    }

    @Test
    void shouldMarkDocumentAsFailedWhenVectorStoreFails() {
        stubRepositorySave();
        when(chunkingService.chunk("有效正文"))
                .thenReturn(List.of("有效正文"));
        doThrow(new RuntimeException("模拟向量服务异常"))
                .when(vectorStore).add(anyList());

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> service.ingest("失败资料", "txt", null, "有效正文")
        );

        assertTrue(exception.getMessage().contains("失败资料"));

        ArgumentCaptor<DocumentEntity> entityCaptor =
                ArgumentCaptor.forClass(DocumentEntity.class);
        verify(documentRepository, times(2)).save(entityCaptor.capture());
        DocumentEntity failedEntity = entityCaptor.getAllValues().get(1);
        assertEquals("FAILED", failedEntity.getStatus());
        assertEquals(0, failedEntity.getChunkCount());
    }

    @Test
    void shouldRejectMissingNameAndSourceType() {
        assertThrows(
                IllegalArgumentException.class,
                () -> service.ingest(" ", "txt", null, "正文")
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> service.ingest("资料", null, null, "正文")
        );

        verify(documentRepository, never()).save(any(DocumentEntity.class));
        verify(vectorStore, never()).add(anyList());
    }

    @Test
    void shouldReplaceOldVectorsAndKeepDocumentId() {
        stubRepositorySave();
        DocumentEntity entity = new DocumentEntity();
        entity.setId(42L);
        entity.setName("旧名称");
        entity.setFileType("note");
        entity.setContentHash("old-hash");
        entity.setStatus("READY");
        entity.setChunkCount(1);
        when(chunkingService.chunk("更新后的正文"))
                .thenReturn(List.of("更新块一", "更新块二"));

        DocumentEntity result = service.replace(
                entity,
                "新名称",
                "note",
                null,
                "更新后的正文"
        );

        assertEquals(42L, result.getId());
        assertEquals("新名称", result.getName());
        assertEquals("READY", result.getStatus());
        assertEquals(2, result.getChunkCount());
        assertNotEquals("old-hash", result.getContentHash());
        verify(vectorStore).delete(any(Filter.Expression.class));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Document>> documentsCaptor =
                ArgumentCaptor.forClass(List.class);
        verify(vectorStore).add(documentsCaptor.capture());
        List<Document> documents = documentsCaptor.getValue();
        assertEquals(2, documents.size());
        assertEquals("42", documents.get(0).getMetadata().get("documentId"));
        assertEquals("新名称", documents.get(0).getMetadata().get("documentName"));
        verify(documentRepository, times(2)).save(entity);
    }

    @Test
    void shouldDeleteVectorsByDocumentId() {
        service.deleteVectors(42L);

        verify(vectorStore).delete(any(Filter.Expression.class));
        verifyNoInteractions(chunkingService, documentRepository);
    }

    private void stubRepositorySave() {
        when(documentRepository.save(any(DocumentEntity.class)))
                .thenAnswer(invocation -> {
                    DocumentEntity entity = invocation.getArgument(0);
                    if (entity.getId() == null) {
                        entity.setId(42L);
                    }
                    return entity;
                });
    }
}
