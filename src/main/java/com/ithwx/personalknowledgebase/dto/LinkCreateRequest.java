package com.ithwx.personalknowledgebase.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LinkCreateRequest(
        @NotBlank(message = "网页地址不能为空")
        String url,

        @Size(max = 255, message = "网页标题长度不能超过 255")
        String title
) {
}
