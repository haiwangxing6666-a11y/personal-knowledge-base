package com.ithwx.personalknowledgebase.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record DocumentUpdateRequest(
        @NotBlank(message = "资料名称不能为空")
        @Size(max = 255, message = "资料名称长度不能超过 255")
        String name,

        @NotBlank(message = "资料正文不能为空")
        String content
) {
}
