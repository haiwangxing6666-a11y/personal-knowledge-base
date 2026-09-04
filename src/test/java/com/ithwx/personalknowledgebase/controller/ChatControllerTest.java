package com.ithwx.personalknowledgebase.controller;

import com.ithwx.personalknowledgebase.dto.AnswerSource;
import com.ithwx.personalknowledgebase.dto.RagAnswerResult;
import com.ithwx.personalknowledgebase.service.RagAnswerService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class ChatControllerTest {

    @Mock
    private RagAnswerService ragAnswerService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(new ChatController(ragAnswerService))
                .build();
    }

    @Test
    void shouldReturnAnswerSourcesAndRetrievalTrace() throws Exception {
        RagAnswerResult answer = new RagAnswerResult(
                "项目支持哪些格式？",
                "支持 TXT、Markdown、PDF 和 DOCX。[证据 1]",
                false,
                "个人知识库支持哪些文档格式？",
                true,
                List.of(new AnswerSource(
                        1L,
                        "项目说明",
                        "md",
                        null,
                        List.of(0, 1)
                ))
        );
        when(ragAnswerService.answer("项目支持哪些格式？")).thenReturn(answer);

        mockMvc.perform(post("/api/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"question":"项目支持哪些格式？"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.question").value("项目支持哪些格式？"))
                .andExpect(jsonPath("$.answer").value(
                        "支持 TXT、Markdown、PDF 和 DOCX。[证据 1]"
                ))
                .andExpect(jsonPath("$.refused").value(false))
                .andExpect(jsonPath("$.rewrittenQuestion").value(
                        "个人知识库支持哪些文档格式？"
                ))
                .andExpect(jsonPath("$.secondSearchExecuted").value(true))
                .andExpect(jsonPath("$.sources.length()").value(1))
                .andExpect(jsonPath("$.sources[0].documentId").value(1))
                .andExpect(jsonPath("$.sources[0].documentName").value("项目说明"))
                .andExpect(jsonPath("$.sources[0].sourceType").value("md"))
                .andExpect(jsonPath("$.sources[0].chunkIndexes[0]").value(0))
                .andExpect(jsonPath("$.sources[0].chunkIndexes[1]").value(1));

        verify(ragAnswerService).answer("项目支持哪些格式？");
    }

    @Test
    void shouldReturnRefusalResult() throws Exception {
        when(ragAnswerService.answer("知识库里没有的问题"))
                .thenReturn(new RagAnswerResult(
                        "知识库里没有的问题",
                        "根据当前知识库资料，暂时无法回答这个问题。",
                        true,
                        null,
                        false,
                        List.of()
                ));

        mockMvc.perform(post("/api/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"question":"知识库里没有的问题"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.refused").value(true))
                .andExpect(jsonPath("$.answer").value(
                        "根据当前知识库资料，暂时无法回答这个问题。"
                ))
                .andExpect(jsonPath("$.sources").isEmpty());
    }

    @Test
    void shouldRejectBlankQuestion() throws Exception {
        mockMvc.perform(post("/api/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"question":"  "}
                                """))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(ragAnswerService);
    }

    @Test
    void shouldRejectMissingQuestion() throws Exception {
        mockMvc.perform(post("/api/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(ragAnswerService);
    }
}
