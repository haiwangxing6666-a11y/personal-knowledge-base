package com.ithwx.personalknowledgebase.service;

import com.ithwx.personalknowledgebase.dto.RetrievedChunk;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RagRetrievalServiceTest {

    @Mock
    private VectorStore vectorStore;

    @Test
    void shouldRejectBlankQuestionBeforeSearching() {
        RagRetrievalService service = new RagRetrievalService(vectorStore, 5, 0.55);

        assertThrows(IllegalArgumentException.class, () -> service.search("  \n "));

        verify(vectorStore, never()).similaritySearch(any(SearchRequest.class));
    }

    @Test
    void shouldUseConfiguredSearchRequestAndReturnSortedRelevantChunks() {
        RagRetrievalService service = new RagRetrievalService(vectorStore, 3, 0.60);
        when(vectorStore.similaritySearch(any(SearchRequest.class)))
                .thenReturn(List.of(
                        document("中等相关", "2", "资料二", "web",
                                "https://example.com/two", 1, 0.72),
                        document("低分噪声", "3", "资料三", "txt",
                                "", 0, 0.40),
                        document("高度相关", "1", "资料一", "note",
                                "", 2, 0.91)
                ));

        List<RetrievedChunk> results = service.search("  如何测试 RAG？  ");

        ArgumentCaptor<SearchRequest> requestCaptor =
                ArgumentCaptor.forClass(SearchRequest.class);
        verify(vectorStore).similaritySearch(requestCaptor.capture());
        SearchRequest request = requestCaptor.getValue();
        assertEquals("如何测试 RAG？", request.getQuery());
        assertEquals(3, request.getTopK());
        assertEquals(0.60, request.getSimilarityThreshold());

        assertEquals(2, results.size());
        assertEquals("高度相关", results.get(0).content());
        assertEquals(1L, results.get(0).documentId());
        assertEquals("资料一", results.get(0).documentName());
        assertEquals("note", results.get(0).sourceType());
        assertNull(results.get(0).sourceUrl());
        assertEquals(2, results.get(0).chunkIndex());
        assertEquals(0.91, results.get(0).similarity());

        assertEquals("中等相关", results.get(1).content());
        assertEquals("https://example.com/two", results.get(1).sourceUrl());
        assertTrue(results.stream().noneMatch(result -> result.content().equals("低分噪声")));
    }

    @Test
    void shouldReturnEmptyListWhenVectorStoreReturnsNoResult() {
        RagRetrievalService service = new RagRetrievalService(vectorStore, 5, 0.55);
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(null);

        assertTrue(service.search("没有命中的问题").isEmpty());
    }

    @Test
    void shouldRejectInvalidConfiguration() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new RagRetrievalService(vectorStore, 0, 0.55)
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> new RagRetrievalService(vectorStore, 5, -0.01)
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> new RagRetrievalService(vectorStore, 5, 1.01)
        );
    }

    private Document document(
            String text,
            String documentId,
            String documentName,
            String sourceType,
            String sourceUrl,
            int chunkIndex,
            double score
    ) {
        return Document.builder()
                .text(text)
                .metadata(Map.of(
                        "documentId", documentId,
                        "documentName", documentName,
                        "sourceType", sourceType,
                        "sourceUrl", sourceUrl,
                        "chunkIndex", chunkIndex
                ))
                .score(score)
                .build();
    }
}
