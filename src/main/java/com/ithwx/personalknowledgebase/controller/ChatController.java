package com.ithwx.personalknowledgebase.controller;

import com.ithwx.personalknowledgebase.dto.ChatRequest;
import com.ithwx.personalknowledgebase.dto.RagAnswerResult;
import com.ithwx.personalknowledgebase.service.RagAnswerService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/chat")
public class ChatController {

    private final RagAnswerService ragAnswerService;

    public ChatController(RagAnswerService ragAnswerService) {
        this.ragAnswerService = ragAnswerService;
    }

    @PostMapping
    public RagAnswerResult answer(@Valid @RequestBody ChatRequest request) {
        return ragAnswerService.answer(request.question());
    }
}
