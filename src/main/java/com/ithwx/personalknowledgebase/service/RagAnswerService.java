package com.ithwx.personalknowledgebase.service;

import com.ithwx.personalknowledgebase.dto.AnswerSource;
import com.ithwx.personalknowledgebase.dto.RagAnswerResult;
import com.ithwx.personalknowledgebase.dto.RetrievedChunk;
import com.ithwx.personalknowledgebase.dto.TwoStageRetrievalResult;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class RagAnswerService {

    static final String NO_EVIDENCE_MESSAGE =
            "根据当前知识库资料，暂时无法回答这个问题。";
    static final String MODEL_FAILURE_MESSAGE =
            "回答服务暂时不可用，请稍后重试。";

    private final TwoStageRetrievalService retrievalService;
    private final ChatModel chatModel;

    public RagAnswerService(
            TwoStageRetrievalService retrievalService,
            ChatModel chatModel
    ) {
        this.retrievalService = retrievalService;
        this.chatModel = chatModel;
    }

    public RagAnswerResult answer(String question) {
        if (question == null || question.isBlank()) {
            throw new IllegalArgumentException("问题不能为空");
        }

        String normalizedQuestion = question.strip();
        TwoStageRetrievalResult retrieval = retrievalService.retrieve(normalizedQuestion);
        if (retrieval.chunks().isEmpty()) {
            return refused(retrieval, NO_EVIDENCE_MESSAGE, List.of());
        }

        List<AnswerSource> sources = collectSources(retrieval.chunks());
        String prompt = buildPrompt(normalizedQuestion, retrieval.chunks());
        try {
            String generatedAnswer = chatModel.call(prompt);
            if (generatedAnswer == null || generatedAnswer.isBlank()) {
                return refused(retrieval, MODEL_FAILURE_MESSAGE, sources);
            }
            return new RagAnswerResult(
                    normalizedQuestion,
                    generatedAnswer.strip(),
                    false,
                    retrieval.rewrittenQuestion(),
                    retrieval.secondSearchExecuted(),
                    sources
            );
        } catch (RuntimeException exception) {
            return refused(retrieval, MODEL_FAILURE_MESSAGE, sources);
        }
    }

    private String buildPrompt(String question, List<RetrievedChunk> chunks) {
        StringBuilder context = new StringBuilder();
        for (int index = 0; index < chunks.size(); index++) {
            RetrievedChunk chunk = chunks.get(index);
            context.append("[证据 ").append(index + 1).append("]\n")
                    .append("资料：").append(chunk.documentName()).append('\n')
                    .append("内容：").append(chunk.content()).append("\n\n");
        }

        return """
                你是个人知识库问答助手。请严格遵守以下规则：
                1. 只能依据给出的知识库证据回答，不得使用外部知识或编造信息。
                2. 如果证据不足以回答，请只回复：根据当前知识库资料，暂时无法回答这个问题。
                3. 回答要准确、简洁，并使用 [证据 1] 这样的编号标明依据。

                用户问题：
                %s

                知识库证据：
                %s
                """.formatted(question, context);
    }

    private List<AnswerSource> collectSources(List<RetrievedChunk> chunks) {
        Map<String, SourceAccumulator> sources = new LinkedHashMap<>();
        for (RetrievedChunk chunk : chunks) {
            String key = sourceIdentity(chunk);
            SourceAccumulator source = sources.computeIfAbsent(
                    key,
                    ignored -> new SourceAccumulator(
                            chunk.documentId(),
                            chunk.documentName(),
                            chunk.sourceType(),
                            chunk.sourceUrl()
                    )
            );
            if (chunk.chunkIndex() != null
                    && chunk.chunkIndex() >= 0
                    && !source.chunkIndexes.contains(chunk.chunkIndex())) {
                source.chunkIndexes.add(chunk.chunkIndex());
            }
        }
        return sources.values().stream().map(SourceAccumulator::toAnswerSource).toList();
    }

    private String sourceIdentity(RetrievedChunk chunk) {
        if (chunk.documentId() != null) {
            return "document:" + chunk.documentId();
        }
        return String.join(
                "|",
                String.valueOf(chunk.sourceType()),
                String.valueOf(chunk.sourceUrl()),
                String.valueOf(chunk.documentName())
        );
    }

    private RagAnswerResult refused(
            TwoStageRetrievalResult retrieval,
            String message,
            List<AnswerSource> sources
    ) {
        return new RagAnswerResult(
                retrieval.originalQuestion(),
                message,
                true,
                retrieval.rewrittenQuestion(),
                retrieval.secondSearchExecuted(),
                sources
        );
    }

    private static final class SourceAccumulator {

        private final Long documentId;
        private final String documentName;
        private final String sourceType;
        private final String sourceUrl;
        private final List<Integer> chunkIndexes = new ArrayList<>();

        private SourceAccumulator(
                Long documentId,
                String documentName,
                String sourceType,
                String sourceUrl
        ) {
            this.documentId = documentId;
            this.documentName = documentName;
            this.sourceType = sourceType;
            this.sourceUrl = sourceUrl;
        }

        private AnswerSource toAnswerSource() {
            return new AnswerSource(
                    documentId,
                    documentName,
                    sourceType,
                    sourceUrl,
                    chunkIndexes
            );
        }
    }
}
