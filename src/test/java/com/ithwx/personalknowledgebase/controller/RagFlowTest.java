package com.ithwx.personalknowledgebase.controller;

import com.ithwx.personalknowledgebase.dto.RetrievedChunk;
import com.ithwx.personalknowledgebase.service.QuestionRewriteService;
import com.ithwx.personalknowledgebase.service.RagAnswerService;
import com.ithwx.personalknowledgebase.service.RagRetrievalService;
import com.ithwx.personalknowledgebase.service.TwoStageRetrievalService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class RagFlowTest {

    @Mock
    private RagRetrievalService retrievalService;

    @Mock
    private QuestionRewriteService questionRewriteService;

    @Mock
    private ChatModel chatModel;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        TwoStageRetrievalService twoStageRetrievalService =
                new TwoStageRetrievalService(
                        retrievalService,
                        questionRewriteService,
                        2,
                        5
                );
        RagAnswerService ragAnswerService =
                new RagAnswerService(twoStageRetrievalService, chatModel);
        mockMvc = MockMvcBuilders
                .standaloneSetup(new ChatController(ragAnswerService))
                .build();
    }

    @Test
    void shouldCompleteTwoStageRagFlowFromHttpRequestToAnswer() throws Exception {
        String originalQuestion = "它支持哪些文件？";
        String rewrittenQuestion = "个人知识库支持哪些文件格式？";
        when(retrievalService.search(originalQuestion))
                .thenReturn(List.of(chunk(1L, 0, "支持 TXT 和 Markdown。", 0.70)));
        when(questionRewriteService.rewrite(originalQuestion))
                .thenReturn(Optional.of(rewrittenQuestion));
        when(retrievalService.search(rewrittenQuestion))
                .thenReturn(List.of(chunk(1L, 1, "还支持 PDF 和 DOCX。", 0.92)));
        when(chatModel.call(contains("还支持 PDF 和 DOCX")))
                .thenReturn("支持 TXT、Markdown、PDF 和 DOCX。[证据 1][证据 2]");

        mockMvc.perform(post("/api/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"question":"它支持哪些文件？"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.refused").value(false))
                .andExpect(jsonPath("$.answer").value(
                        "支持 TXT、Markdown、PDF 和 DOCX。[证据 1][证据 2]"
                ))
                .andExpect(jsonPath("$.rewrittenQuestion").value(rewrittenQuestion))
                .andExpect(jsonPath("$.secondSearchExecuted").value(true))
                .andExpect(jsonPath("$.sources.length()").value(1))
                .andExpect(jsonPath("$.sources[0].documentName").value("项目说明"))
                .andExpect(jsonPath("$.sources[0].chunkIndexes.length()").value(2));

        var calls = inOrder(retrievalService, questionRewriteService, chatModel);
        calls.verify(retrievalService).search(originalQuestion);
        calls.verify(questionRewriteService).rewrite(originalQuestion);
        calls.verify(retrievalService).search(rewrittenQuestion);
        calls.verify(chatModel).call(contains("还支持 PDF 和 DOCX"));
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
                "项目说明",
                "md",
                null,
                chunkIndex,
                similarity
        );
    }
}
