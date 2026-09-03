package com.ithwx.personalknowledgebase.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record NoteCreateRequest(
        @NotBlank(message = "笔记标题不能为空")
        @Size(max = 255, message = "笔记标题长度不能超过 255")
        String title,

        @NotBlank(message = "笔记正文不能为空")
        String content
) {
}
