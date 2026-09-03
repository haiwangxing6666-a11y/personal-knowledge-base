package com.ithwx.personalknowledgebase.service;

import com.ithwx.personalknowledgebase.dto.WebPage;
import com.ithwx.personalknowledgebase.entity.DocumentEntity;
import com.ithwx.personalknowledgebase.repository.DocumentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DocumentManagementServiceTest {

    @Mock
    private DocumentParserService documentParserService;

    @Mock
    private WebContentService webContentService;

    @Mock
    private KnowledgeIngestionService knowledgeIngestionService;

    @Mock
    private DocumentRepository documentRepository;

    private DocumentManagementService service;

    @BeforeEach
    void setUp() {
        service = new DocumentManagementService(
                documentParserService,
                webContentService,
                knowledgeIngestionService,
                documentRepository
        );
    }

    @Test
    void shouldParseAndIngestUppercaseFileType() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "课程资料.TXT",
                "text/plain",
                "中文正文".getBytes(StandardCharsets.UTF_8)
        );
        DocumentEntity expected = document(1L, "课程资料.TXT", "txt");
        when(documentParserService.parse(file)).thenReturn("中文正文");
        when(knowledgeIngestionService.ingest(
                "课程资料.TXT", "txt", null, "中文正文"
        )).thenReturn(expected);

        DocumentEntity result = service.upload(file);

        assertSame(expected, result);
        verify(documentParserService).parse(file);
        verify(knowledgeIngestionService).ingest(
                "课程资料.TXT", "txt", null, "中文正文"
        );
    }

    @Test
    void shouldRejectEmptyFileBeforeParsing() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "empty.txt", "text/plain", new byte[0]
        );

        assertThrows(IllegalArgumentException.class, () -> service.upload(file));

        verifyNoInteractions(documentParserService, knowledgeIngestionService);
    }

    @Test
    void shouldCreateNoteThroughIngestionService() {
        DocumentEntity expected = document(2L, "学习笔记", "note");
        when(knowledgeIngestionService.ingest(
                "学习笔记", "note", null, "笔记正文"
        )).thenReturn(expected);

        DocumentEntity result = service.createNote("学习笔记", "笔记正文");

        assertSame(expected, result);
    }

    @Test
    void shouldUseFetchedTitleAndUrlForWebPage() {
        WebPage page = new WebPage(
                "https://example.com/article",
                "抓取到的标题",
                "网页正文"
        );
        DocumentEntity expected = document(3L, "抓取到的标题", "web");
        expected.setSourceUrl(page.url());
        when(webContentService.fetch(page.url())).thenReturn(page);
        when(knowledgeIngestionService.ingest(
                page.title(), "web", page.url(), page.text()
        )).thenReturn(expected);

        DocumentEntity result = service.createLink(page.url(), "  ");

        assertSame(expected, result);
        verify(knowledgeIngestionService).ingest(
                page.title(), "web", page.url(), page.text()
        );
    }

    @Test
    void shouldReturnDocumentsInRepositoryOrder() {
        List<DocumentEntity> expected = List.of(
                document(2L, "新资料", "note"),
                document(1L, "旧资料", "txt")
        );
        when(documentRepository.findAllByOrderByUploadTimeDesc())
                .thenReturn(expected);

        assertEquals(expected, service.list());
        verify(documentRepository).findAllByOrderByUploadTimeDesc();
        verify(knowledgeIngestionService, never()).ingest(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any()
        );
    }

    private DocumentEntity document(Long id, String name, String fileType) {
        DocumentEntity entity = new DocumentEntity();
        entity.setId(id);
        entity.setName(name);
        entity.setFileType(fileType);
        entity.setStatus("READY");
        entity.setChunkCount(1);
        return entity;
    }
}
