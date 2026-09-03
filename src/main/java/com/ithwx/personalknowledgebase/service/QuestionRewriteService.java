package com.ithwx.personalknowledgebase.service;

import org.springframework.ai.chat.model.ChatModel;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class QuestionRewriteService {

    private static final int MAX_REWRITTEN_QUESTION_LENGTH = 500;

    private final ChatModel chatModel;

    public QuestionRewriteService(ChatModel chatModel) {
        this.chatModel = chatModel;
    }

    public Optional<String> rewrite(String question) {
        if (question == null || question.isBlank()) {
            throw new IllegalArgumentException("待改写的问题不能为空");
        }

        String prompt = """
                你是知识库检索问题改写器。请把用户问题改写为一个独立、清晰、适合向量检索的问题。
                不要回答问题，不要补充用户未提供的事实，只输出改写后的问题。

                用户问题：
                %s
                """.formatted(question.strip());

        try {
            return normalize(chatModel.call(prompt));
        } catch (RuntimeException exception) {
            return Optional.empty();
        }
    }

    private Optional<String> normalize(String rewrittenQuestion) {
        if (rewrittenQuestion == null || rewrittenQuestion.isBlank()) {
            return Optional.empty();
        }

        String normalized = rewrittenQuestion.strip();
        if (normalized.length() >= 2
                && ((normalized.startsWith("\"") && normalized.endsWith("\""))
                || (normalized.startsWith("“") && normalized.endsWith("”")))) {
            normalized = normalized.substring(1, normalized.length() - 1).strip();
        }
        if (normalized.length() > MAX_REWRITTEN_QUESTION_LENGTH) {
            normalized = normalized.substring(0, MAX_REWRITTEN_QUESTION_LENGTH).strip();
        }
        return normalized.isBlank() ? Optional.empty() : Optional.of(normalized);
    }
}
