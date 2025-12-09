package com.nova.support.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class KnowledgeBaseResponse {
    private Long id;
    private Long projectId;
    private String title;
    private String content;
    private String sourceType;
    private String sourceUrl;
    private LocalDateTime createdAt;
}
