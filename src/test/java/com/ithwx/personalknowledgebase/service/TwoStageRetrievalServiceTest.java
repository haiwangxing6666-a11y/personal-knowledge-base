package com.ithwx.personalknowledgebase.service;

import com.ithwx.personalknowledgebase.dto.RetrievedChunk;
import com.ithwx.personalknowledgebase.dto.TwoStageRetrievalResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TwoStageRetrievalServiceTest {

    @Mock
    private RagRetrievalService retrievalService;

    @Mock
    private QuestionRewriteService questionRewriteService;

    @Test
    void shouldStopAfterFirstSearchWhenResultsAreEnough() {
        TwoStageRetrievalService service = service(2, 5);
        List<RetrievedChunk> firstResults = List.of(
                chunk(1L, 0, "第一块", 0.91),
                chunk(1L, 1, "第二块", 0.85)
        );
        when(retrievalService.search("原问题")).thenReturn(firstResults);

        TwoStageRetrievalResult result = service.retrieve("  原问题  ");

        assertEquals("原问题", result.originalQuestion());
        assertNull(result.rewrittenQuestion());
        assertFalse(result.secondSearchExecuted());
        assertEquals(firstResults, result.chunks());
        verify(questionRewriteService, never()).rewrite("原问题");
    }

    @Test
    void shouldRewriteOnceAndRunSecondSearchWhenFirstResultsAreWeak() {
        TwoStageRetrievalService service = service(2, 5);
        when(retrievalService.search("它怎么工作"))
                .thenReturn(List.of(chunk(1L, 0, "旧结果", 0.61)));
        when(questionRewriteService.rewrite("它怎么工作"))
                .thenReturn(Optional.of("RAG 检索流程如何工作"));
        when(retrievalService.search("RAG 检索流程如何工作"))
                .thenReturn(List.of(chunk(2L, 0, "新结果", 0.90)));

        TwoStageRetrievalResult result = service.retrieve("它怎么工作");

        assertTrue(result.secondSearchExecuted());
        assertEquals("RAG 检索流程如何工作", result.rewrittenQuestion());
        assertEquals(List.of("新结果", "旧结果"),
                result.chunks().stream().map(RetrievedChunk::content).toList());
        InOrder inOrder = inOrder(retrievalService, questionRewriteService);
        inOrder.verify(retrievalService).search("它怎么工作");
        inOrder.verify(questionRewriteService).rewrite("它怎么工作");
        inOrder.verify(retrievalService).search("RAG 检索流程如何工作");
    }

    @Test
    void shouldDeduplicateKeepHigherScoreSortAndLimitMergedResults() {
        TwoStageRetrievalService service = service(3, 2);
        when(retrievalService.search("原问题")).thenReturn(List.of(
                chunk(1L, 0, "重复旧结果", 0.60),
                chunk(2L, 0, "结果二", 0.75)
        ));
        when(questionRewriteService.rewrite("原问题"))
                .thenReturn(Optional.of("改写问题"));
        when(retrievalService.search("改写问题")).thenReturn(List.of(
                chunk(1L, 0, "重复新结果", 0.95),
                chunk(3L, 0, "结果三", 0.85)
        ));

        TwoStageRetrievalResult result = service.retrieve("原问题");

        assertEquals(List.of("重复新结果", "结果三"),
                result.chunks().stream().map(RetrievedChunk::content).toList());
    }

    @Test
    void shouldKeepFirstResultsWhenRewriteIsUnavailableOrUnchanged() {
        TwoStageRetrievalService service = service(2, 5);
        List<RetrievedChunk> firstResults = List.of(chunk(1L, 0, "结果", 0.70));
        when(retrievalService.search("原问题")).thenReturn(firstResults);
        when(questionRewriteService.rewrite("原问题"))
                .thenReturn(Optional.of("原问题"));

        TwoStageRetrievalResult result = service.retrieve("原问题");

        assertFalse(result.secondSearchExecuted());
        assertEquals(firstResults, result.chunks());
        verify(retrievalService).search("原问题");
    }

    @Test
    void shouldRejectBlankQuestionAndInvalidConfiguration() {
        TwoStageRetrievalService service = service(2, 5);

        assertThrows(IllegalArgumentException.class, () -> service.retrieve("  "));
        assertThrows(IllegalArgumentException.class, () -> service(0, 5));
        assertThrows(IllegalArgumentException.class, () -> service(2, 0));
    }

    private TwoStageRetrievalService service(int retryMinHits, int maxResults) {
        return new TwoStageRetrievalService(
                retrievalService,
                questionRewriteService,
                retryMinHits,
                maxResults
        );
    }

    private RetrievedChunk chunk(
            Long documentId,
            int chunkIndex,
            String content,
            double similarity
    ) {
        return new RetrievedChunk(
                content,
                documentId,
                "测试资料",
                "note",
                null,
                chunkIndex,
                similarity
        );
    }
}
