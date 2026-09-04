package com.ithwx.personalknowledgebase.service;

import com.ithwx.personalknowledgebase.dto.AnswerSource;
import com.ithwx.personalknowledgebase.dto.RagAnswerResult;
import com.ithwx.personalknowledgebase.dto.RetrievedChunk;
import com.ithwx.personalknowledgebase.dto.TwoStageRetrievalResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.model.ChatModel;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RagAnswerServiceTest {

    @Mock
    private TwoStageRetrievalService retrievalService;

    @Mock
    private ChatModel chatModel;

    @Test
    void shouldAnswerFromEvidenceAndReturnDeduplicatedSources() {
        RagAnswerService service = new RagAnswerService(retrievalService, chatModel);
        when(retrievalService.retrieve("项目支持哪些格式？"))
                .thenReturn(retrieval(List.of(
                        chunk(1L, "使用说明", "txt", null, 0,
                                "系统支持 TXT 和 Markdown。"),
                        chunk(1L, "使用说明", "txt", null, 1,
                                "系统还支持 PDF 和 DOCX。"),
                        chunk(2L, "项目主页", "web", "https://example.com", 0,
                                "上传后会自动解析。")
                )));
        when(chatModel.call(contains("系统支持 TXT 和 Markdown")))
                .thenReturn("支持 TXT、Markdown、PDF 和 DOCX。[证据 1][证据 2]");

        RagAnswerResult result = service.answer("  项目支持哪些格式？  ");

        assertFalse(result.refused());
        assertEquals("支持 TXT、Markdown、PDF 和 DOCX。[证据 1][证据 2]", result.answer());
        assertEquals("改写后的问题", result.rewrittenQuestion());
        assertTrue(result.secondSearchExecuted());
        assertEquals(2, result.sources().size());
        AnswerSource firstSource = result.sources().get(0);
        assertEquals(1L, firstSource.documentId());
        assertEquals("使用说明", firstSource.documentName());
        assertEquals(List.of(0, 1), firstSource.chunkIndexes());
        assertNull(firstSource.sourceUrl());
        assertEquals("https://example.com", result.sources().get(1).sourceUrl());
        verify(chatModel).call(contains("用户问题：\n项目支持哪些格式？"));
    }

    @Test
    void shouldRefuseWithoutCallingModelWhenNoEvidenceExists() {
        RagAnswerService service = new RagAnswerService(retrievalService, chatModel);
        TwoStageRetrievalResult emptyRetrieval = new TwoStageRetrievalResult(
                "未知问题",
                null,
                false,
                List.of()
        );
        when(retrievalService.retrieve("未知问题")).thenReturn(emptyRetrieval);

        RagAnswerResult result = service.answer("未知问题");

        assertTrue(result.refused());
        assertEquals(RagAnswerService.NO_EVIDENCE_MESSAGE, result.answer());
        assertTrue(result.sources().isEmpty());
        verify(chatModel, never()).call(org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void shouldFailSafelyWhenModelThrowsExceptionOrReturnsBlank() {
        RagAnswerService service = new RagAnswerService(retrievalService, chatModel);
        TwoStageRetrievalResult retrieval = retrieval(List.of(
                chunk(1L, "资料", "note", null, 0, "已有证据")
        ));
        when(retrievalService.retrieve("异常问题")).thenReturn(retrieval);
        when(retrievalService.retrieve("空回答问题")).thenReturn(retrieval);
        when(chatModel.call(contains("异常问题")))
                .thenThrow(new IllegalStateException("模型不可用"));
        when(chatModel.call(contains("空回答问题"))).thenReturn("  ");

        RagAnswerResult failed = service.answer("异常问题");
        RagAnswerResult blank = service.answer("空回答问题");

        assertTrue(failed.refused());
        assertEquals(RagAnswerService.MODEL_FAILURE_MESSAGE, failed.answer());
        assertEquals(1, failed.sources().size());
        assertTrue(blank.refused());
        assertEquals(RagAnswerService.MODEL_FAILURE_MESSAGE, blank.answer());
    }

    @Test
    void shouldRejectBlankQuestionBeforeRetrieval() {
        RagAnswerService service = new RagAnswerService(retrievalService, chatModel);

        assertThrows(IllegalArgumentException.class, () -> service.answer(" \n "));
        verify(retrievalService, never()).retrieve(org.mockito.ArgumentMatchers.anyString());
        verify(chatModel, never()).call(org.mockito.ArgumentMatchers.anyString());
    }

    private TwoStageRetrievalResult retrieval(List<RetrievedChunk> chunks) {
        return new TwoStageRetrievalResult(
                "项目支持哪些格式？",
                "改写后的问题",
                true,
                chunks
        );
    }

    private RetrievedChunk chunk(
            Long documentId,
            String documentName,
            String sourceType,
            String sourceUrl,
            int chunkIndex,
            String content
    ) {
        return new RetrievedChunk(
                content,
                documentId,
                documentName,
                sourceType,
                sourceUrl,
                chunkIndex,
                0.90
        );
    }
}
